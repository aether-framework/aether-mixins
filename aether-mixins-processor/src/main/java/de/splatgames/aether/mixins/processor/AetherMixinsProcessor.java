package de.splatgames.aether.mixins.processor;

import com.google.auto.service.AutoService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.splatgames.aether.mixins.core.api.Inject;
import de.splatgames.aether.mixins.core.api.Mixin;
import de.splatgames.aether.mixins.core.api.Redirect;
import de.splatgames.aether.mixins.processor.emit.EntryFactory;
import de.splatgames.aether.mixins.processor.emit.MixinsYamlBuilder;
import de.splatgames.aether.mixins.processor.emit.RefmapBuilder;
import de.splatgames.aether.mixins.processor.io.ResourceWriter;
import de.splatgames.aether.mixins.processor.model.CollectedEntry;
import de.splatgames.aether.mixins.processor.model.CollectedMixin;
import de.splatgames.aether.mixins.processor.util.DescriptorUtil;
import org.jetbrains.annotations.NotNull;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedOptions;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Annotation processor that collects {@link Mixin}, {@link Inject}, and {@link Redirect}
 * declarations from source code and generates:
 *
 * <ul>
 *   <li>a refmap JSON (schema {@code 1}) at {@code /mixins/&lt;artifact&gt;.refmap.json}, and</li>
 *   <li>a minimal {@code /mixins.yml} that references the generated refmap.</li>
 * </ul>
 *
 * <h2>Purpose</h2>
 * <p>
 * The generated resources allow the runtime (see {@code MixinsRuntime}) to discover mixins and
 * their hooks (injects/redirects) without reflection at runtime. The processor runs during
 * compilation and writes into the class output location.
 * </p>
 *
 * <h2>Conventions</h2>
 * <ul>
 *   <li><b>Entry id:</b> The hook entry id is the method signature {@code name+descriptor}
 *       (e.g., {@code onHead()V}). This gives the weaver a deterministic way to locate hook methods.</li>
 *   <li><b>Targets:</b> Taken from {@link Mixin#targets()} and {@link Mixin#value()}; use binary names
 *       (e.g., {@code com.example.Foo}).</li>
 *   <li><b>Descriptors:</b> JVM format like {@code (I)I}; redirect owners use internal JVM names
 *       (e.g., {@code com/example/Foo}).</li>
 * </ul>
 *
 * <h2>Configuration</h2>
 * <p>
 * You can override the artifact id used in the output file names via the compiler option
 * {@code -Aaether.mixins.artifact=&lt;id&gt;}. Defaults to {@code app}.
 * </p>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * The processor is created and invoked by the annotation processing tool in a single-threaded manner;
 * no additional synchronization is required here.
 * </p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
@SupportedSourceVersion(SourceVersion.RELEASE_17)
@SupportedOptions(AetherMixinsProcessor.OPT_ARTIFACT)
@AutoService(Processor.class)
public final class AetherMixinsProcessor extends AbstractProcessor {

    /**
     * Compiler option key that specifies the artifact id used for output files.
     *
     * <p>Usage example: {@code -Aaether.mixins.artifact=my-artifact}</p>
     */
    public static final String OPT_ARTIFACT = "aether.mixins.artifact";

    /**
     * Shared, pretty-printing {@link Gson} instance for emitting JSON.
     */
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    /**
     * Compiler messaging facility.
     */
    private Messager messager;

    /**
     * File creation facility for generated resources.
     */
    private Filer filer;

    /**
     * Utility for element queries.
     */
    private Elements elements;

    /**
     * Utility for type queries.
     */
    private Types types;

    /**
     * Guard to ensure resources are written only once across rounds.
     */
    private boolean written = false;

    /**
     * Public no-arg constructor required by the annotation processing tool.
     * <p>Initialization of processing utilities happens in {@link #init(ProcessingEnvironment)}.</p>
     */
    public AetherMixinsProcessor() {
        // no-op
    }

    /**
     * Initializes processing utilities and services for this processor instance.
     *
     * @param processingEnv the current processing environment providing utilities and options; never {@code null}
     */
    @Override
    public synchronized void init(@NotNull final ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.filer = processingEnv.getFiler();
        this.elements = processingEnv.getElementUtils();
        this.types = processingEnv.getTypeUtils();
    }

    /**
     * Declares the fully qualified annotation types supported by this processor.
     *
     * @return an immutable set containing the canonical names of {@link Mixin}, {@link Inject}, and {@link Redirect}
     */
    @NotNull
    @Override
    public Set<String> getSupportedAnnotationTypes() {
        return Set.of(
                Mixin.class.getCanonicalName(),
                Inject.class.getCanonicalName(),
                Redirect.class.getCanonicalName()
        );
    }

    /**
     * Main processing entry point for the annotation processor.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>Collect all types annotated with {@link Mixin}.</li>
     *   <li>For each mixin, scan methods for {@link Inject} and {@link Redirect} and collect entries.</li>
     *   <li>Build a refmap JSON (schema {@code 1}) and a minimal {@code mixins.yml} referencing it.</li>
     *   <li>Write both resources to {@code CLASS_OUTPUT} (once per compilation, guarded by {@link #written}).</li>
     * </ol>
     *
     * <p>If no {@code @Mixin}-annotated types are found, the method returns {@code false} without emitting resources.</p>
     *
     * @param annotations the set of annotations requested to be processed (ignored here; discovery is by query)
     * @param roundEnv    environment providing access to elements for the current and prior rounds
     * @return {@code false} to allow other processors to process these annotations as well
     */
    @Override
    public boolean process(@NotNull final Set<? extends TypeElement> annotations,
                           @NotNull final RoundEnvironment roundEnv) {
        if (this.written || roundEnv.processingOver()) {
            return false;
        }

        // 1) Collect @Mixin classes
        final Map<String, CollectedMixin> collected = new LinkedHashMap<>();
        for (final TypeElement mixinType : ElementFilter.typesIn(roundEnv.getElementsAnnotatedWith(Mixin.class))) {
            final Mixin mixin = mixinType.getAnnotation(Mixin.class);
            if (mixin == null) continue;

            final CollectedMixin cm = new CollectedMixin(
                    mixinType,
                    DescriptorUtil.binaryName(mixinType, this.elements),
                    List.of(Stream.of(mixin.targets(), Arrays.stream(mixin.value()).map(Class::getName).toArray(String[]::new)).flatMap(Arrays::stream).toArray(String[]::new)),
                    mixin.priority(),
                    List.of(mixin.groups()),
                    List.of(mixin.requires()),
                    List.of(mixin.conflictsWith())
            );
            collected.put(cm.getClassName(), cm);
        }

        if (collected.isEmpty()) {
            // Nothing to emit in this compilation unit
            return false;
        }

        // 2) Scan methods for @Inject/@Redirect
        for (final CollectedMixin cm : collected.values()) {
            ElementFilter.methodsIn(cm.getType().getEnclosedElements()).forEach(method -> {
                final Inject inj = method.getAnnotation(Inject.class);
                if (inj != null) {
                    final CollectedEntry e = EntryFactory.injectEntry(method, inj, this.elements, this.types);
                    cm.getEntries().add(e);
                }
                final Redirect red = method.getAnnotation(Redirect.class);
                if (red != null) {
                    final CollectedEntry e = EntryFactory.redirectEntry(method, red, this.elements, this.types);
                    cm.getEntries().add(e);
                }
            });
        }

        // 3) Build JSON + YAML
        final String artifact = this.inferArtifactId();
        final String refmapPath = "mixins/" + artifact + ".refmap.json";
        final JsonObject refmap = RefmapBuilder.build(collected.values(), 1);
        final String mixinsYaml = MixinsYamlBuilder.build(artifact, refmapPath);

        // 4) Write resources
        try {
            ResourceWriter.write(this.filer, "mixins.yml", mixinsYaml.getBytes(StandardCharsets.UTF_8));
            ResourceWriter.write(this.filer, refmapPath, GSON.toJson(refmap).getBytes(StandardCharsets.UTF_8));
            this.written = true;
        } catch (final IOException ex) {
            this.error("Failed writing generated mixins resources: " + ex.getMessage());
        }

        return false; // let other processors continue
    }

    /**
     * Derives the artifact id used to name the refmap file and the mixin set in {@code mixins.yml}.
     *
     * <p>Resolution:</p>
     * <ol>
     *   <li>Read {@code -A}{@link #OPT_ARTIFACT} from the processing environment options.</li>
     *   <li>Fallback to {@code "app"} when the option is absent or blank.</li>
     * </ol>
     *
     * @return the artifact id to use; never {@code null}
     */
    @NotNull
    private String inferArtifactId() {
        final String opt = this.processingEnv.getOptions().get(OPT_ARTIFACT);
        if (opt != null && !opt.isBlank()) return opt.trim();
        return "app";
    }

    /**
     * Emits a compiler error message prefixed with {@code [Aether Mixins]}.
     *
     * @param msg human-readable message text; must not be {@code null}
     */
    private void error(@NotNull final String msg) {
        this.messager.printMessage(Diagnostic.Kind.ERROR, "[Aether Mixins] " + Objects.requireNonNull(msg, "msg"));
    }
}
