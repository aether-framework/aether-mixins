package de.splatgames.aether.mixins.agent;

import de.splatgames.aether.mixins.agent.io.AgentSource;
import de.splatgames.aether.mixins.agent.io.CapturingSink;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.config.runtime.RuntimeConfig;
import de.splatgames.aether.mixins.core.plan.PlannedMixin;
import de.splatgames.aether.mixins.core.plan.WeavePlan;
import de.splatgames.aether.mixins.core.weaver.spi.ClassSource;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveRequest;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveResult;
import de.splatgames.aether.mixins.core.weaver.spi.Weaver;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * {@link ClassFileTransformer} that applies a pre-sliced {@link WeavePlan} to a class
 * at load/retransform time.
 *
 * <p>This transformer is <em>class-targeted</em>: it only triggers when a plan exists
 * for the exact internal JVM name (e.g. {@code com/example/Foo}) being transformed.
 * If no plan exists for the class or the plan has no mixins, it returns {@code null}
 * immediately to indicate "no change".</p>
 *
 * <h2>How it works</h2>
 * <ol>
 *   <li>Look up the per-class plan in {@code perClassPlans} using the internal class name.</li>
 *   <li>Build a tiny {@link ClassSource} (the {@link AgentSource}) that serves the current
 *       class bytes and delegates other classes to the class loader.</li>
 *   <li>Use a {@link CapturingSink} to capture transformed bytes only for the class being transformed.</li>
 *   <li>Construct a {@link WeaveRequest} and invoke the {@link Weaver}.</li>
 *   <li>Log any diagnostics from {@link ConfigProblems} and return the captured bytes
 *       (or {@code null} if unchanged).</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 * <ul>
 *   <li>If an exception occurs and {@code safe_mode} is <b>enabled</b>, the error is logged and {@code null} is returned.</li>
 *   <li>If an exception occurs and {@code safe_mode} is <b>disabled</b>, the transformer logs details and halts the JVM
 *       with exit code {@code 2}. This is intentional defensive behavior for critical deployments.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>Instances are thread-safe under the assumption that {@code perClassPlans} is a concurrent map
 * (or immutable snapshot) supplied by the caller, and that {@link Weaver#weave} is safe to call from
 * multiple threads (typically one per transforming class). The transformer itself holds no mutable
 * per-call state.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
final class AgentTransformer implements ClassFileTransformer {
    /**
     * Pre-sliced plans indexed by internal JVM class name (e.g. {@code com/example/Foo}).
     * Only classes present as keys are eligible for transformation.
     */
    @NotNull
    private final Map<String, WeavePlan> perClassPlans;

    /**
     * Weaving implementation used to apply planned mixins.
     */
    @NotNull
    private final Weaver weaver;

    /**
     * Supplies the current {@link RuntimeConfig} (e.g., to read {@code safe_mode}) at transform time.
     * A supplier is used so the runtime can be swapped without recreating the transformer.
     */
    @NotNull
    private final Supplier<RuntimeConfig> runtimeSupplier;

    /**
     * Creates a new transformer that applies pre-sliced per-class plans.
     *
     * @param perClassPlans   map from internal class name to {@link WeavePlan}, not {@code null}
     * @param weaver          weaver implementation, not {@code null}
     * @param runtimeSupplier supplier of {@link RuntimeConfig} used for each invocation, not {@code null}
     * @throws NullPointerException if any argument is {@code null}
     */
    AgentTransformer(@NotNull final Map<String, WeavePlan> perClassPlans,
                     @NotNull final Weaver weaver,
                     @NotNull final Supplier<RuntimeConfig> runtimeSupplier) {
        this.perClassPlans = Objects.requireNonNull(perClassPlans, "perClassPlans");
        this.weaver = Objects.requireNonNull(weaver, "weaver");
        this.runtimeSupplier = Objects.requireNonNull(runtimeSupplier, "runtimeSupplier");
    }

    /**
     * Applies the per-class plan (if any) to the class being loaded/retransformed.
     *
     * <p>Returns {@code null} in the following cases:</p>
     * <ul>
     *   <li>{@code className} is {@code null}</li>
     *   <li>{@code classfileBuffer} is {@code null}</li>
     *   <li>No plan is registered for {@code className}</li>
     *   <li>The plan for {@code className} contains no mixins</li>
     *   <li>The weaver produced no changes</li>
     *   <li>An error occurred and {@code safe_mode} is enabled</li>
     * </ul>
     *
     * @param module              the module of the class being transformed (Java 9+), may be {@code null}
     * @param loader              defining loader of the class, may be {@code null}
     * @param className           internal JVM name (slash-separated), may be {@code null}
     * @param classBeingRedefined non-null when redefining/retransforming, else {@code null}
     * @param protectionDomain    class protection domain, may be {@code null}
     * @param classfileBuffer     original class bytes, may be {@code null}
     * @return transformed bytes or {@code null} if unchanged/ignored/error in safe mode
     */
    @Override
    public byte[] transform(@Nullable final Module module,
                            @Nullable final ClassLoader loader,
                            @Nullable final String className,
                            @Nullable final Class<?> classBeingRedefined,
                            @Nullable final ProtectionDomain protectionDomain,
                            @Nullable final byte[] classfileBuffer) {
        if (className == null || classfileBuffer == null) {
            return null;
        }

        final WeavePlan plan = this.perClassPlans.get(className);
        if (plan == null || plan.getMixins().isEmpty()) {
            return null;
        }

        final CapturingSink sink = new CapturingSink(className);
        final ClassSource source = new AgentSource(loader, className, classfileBuffer);

        final RuntimeConfig runtime = this.runtimeSupplier.get();
        final WeaveRequest request = WeaveRequest.of(plan, source, sink, runtime);
        final ConfigProblems problems = new ConfigProblems("agent/" + className);

        try {
            final WeaveResult result = this.weaver.weave(request, problems);
            for (final var p : problems.all()) {
                System.err.println("[Aether Mixins] " + p.severity() + " @ " + p.path() + " — " + p.message());
            }
            // Returning null signals "no change" to the JVM.
            return sink.getCaptured();
        } catch (final Throwable t) {
            if (!runtime.isSafeMode()) {
                final String mixinList = plan.getMixins().stream()
                        .map(PlannedMixin::getClassName)
                        .collect(Collectors.joining(", "));
                System.err.println("[Aether Mixins] FATAL while weaving class " + className +
                        " (mixins: " + (mixinList.isEmpty() ? "<none>" : mixinList) + "): " + t);
                System.err.println("[Aether Mixins] Aborting JVM as safe_mode is disabled.");
                System.err.println("[Aether Mixins] Do not report this as a bug before verifying it’s not on your side.");
                t.printStackTrace(System.err);
                System.err.flush();
                Runtime.getRuntime().halt(2);
                return null; // Unreachable if halt succeeds; return to satisfy the signature.
            }
            System.err.println("[Aether Mixins] ERROR while weaving " + className + ": " + t);
            return null;
        }
    }
}
