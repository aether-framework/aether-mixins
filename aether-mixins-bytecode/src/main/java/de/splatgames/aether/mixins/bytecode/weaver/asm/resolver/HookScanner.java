package de.splatgames.aether.mixins.bytecode.weaver.asm.resolver;

import de.splatgames.aether.mixins.bytecode.weaver.hook.CandidateHook;
import de.splatgames.aether.mixins.bytecode.weaver.hook.HookInvocation;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.plan.PlannedEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

import static org.objectweb.asm.Opcodes.ACC_STATIC;
import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Scans a mixin class (provided as a byte array) for static methods annotated with
 * {@code @Inject} or {@code @Redirect}. This scanner operates purely on bytecode (ASM)
 * and does not load the class.
 *
 * <p>For each matching method, a {@link CandidateHook} is emitted with the method
 * name, JVM descriptor, and the optional annotation {@code id} value.</p>
 *
 * @author Erik Pförtner
 * @since 0.1.0
 */
public final class HookScanner {

    /**
     * Descriptor of the required annotation ({@code @Inject} or {@code @Redirect}).
     */
    @NotNull
    private final String requiredAnnDesc;

    /**
     * Creates a new scanner for the given planned entry kind.
     *
     * @param kind determines whether to look for {@code @Inject} (when {@link PlannedEntry.Kind#INJECT})
     *             or {@code @Redirect} (otherwise); must not be {@code null}
     */
    public HookScanner(@NotNull final PlannedEntry.Kind kind) {
        this.requiredAnnDesc = (kind == PlannedEntry.Kind.INJECT)
                ? Type.getDescriptor(de.splatgames.aether.mixins.core.api.Inject.class)
                : Type.getDescriptor(de.splatgames.aether.mixins.core.api.Redirect.class);
    }

    /**
     * Scans the provided class byte array and collects all valid hook candidates.
     *
     * <p>A method is considered a valid candidate if and only if it is declared {@code static}
     * and carries the required annotation.</p>
     *
     * @param classBytes   the raw class bytes to parse
     * @param internalName the internal JVM class name used for diagnostics (e.g., {@code com/example/MyMixin})
     * @param problems     diagnostics sink to receive warnings when no candidates are found
     * @param path         human-readable diagnostics path used in messages (e.g., {@code planner/MyMixin#0})
     * @return a list of discovered {@link CandidateHook}s; empty if none were found
     */
    @NotNull
    public List<CandidateHook> scan(
            @NotNull final byte[] classBytes,
            @NotNull final String internalName,
            @NotNull final ConfigProblems problems,
            @NotNull final String path
    ) {
        final List<CandidateHook> out = new ArrayList<>();

        final ClassReader cr = new ClassReader(classBytes);
        cr.accept(new ClassVisitor(ASM9) {
            @Override
            public MethodVisitor visitMethod(final int access, final String name, final String desc,
                                             final String signature, final String @Nullable [] exceptions) {
                final HookInvocation invoc = (access & ACC_STATIC) != 0
                        ? HookInvocation.STATIC
                        : HookInvocation.INSTANCE;

                final CandidateHook holder = new CandidateHook(name, desc, invoc);
                final boolean[] hasRequired = {false};

                return new MethodVisitor(ASM9) {
                    @Override
                    public AnnotationVisitor visitAnnotation(final String descriptor, final boolean visible) {
                        if (HookScanner.this.requiredAnnDesc.equals(descriptor)) {
                            hasRequired[0] = true;
                            return new AnnotationVisitor(ASM9) {
                                @Override
                                public void visit(final String n, final Object v) {
                                    if ("id".equals(n) && v instanceof String s) {
                                        holder.setAnnotationId(s);
                                    }
                                }
                            };
                        }
                        return null;
                    }

                    @Override
                    public void visitEnd() {
                        if (hasRequired[0]) {
                            // Mark as "annotation seen" with an empty id if none was provided explicitly.
                            if (holder.getAnnotationId() == null) {
                                holder.setAnnotationId("");
                            }
                            out.add(holder);
                        }
                    }
                };
            }
        }, ClassReader.SKIP_FRAMES | ClassReader.SKIP_DEBUG);

        if (out.isEmpty()) {
            problems.warn(path, "No @" + simpleAnn(this.requiredAnnDesc) + " static methods found in " + internalName);
        }
        return out;
    }

    /**
     * Converts an annotation descriptor into a simple short type name without package.
     *
     * <p>Example: {@code Lcom/example/Inject;} → {@code Inject}</p>
     *
     * @param desc the JVM descriptor of the annotation (e.g., {@code Lpkg/Inject;})
     * @return the short type name derived from the descriptor (e.g., {@code Inject})
     */
    @NotNull
    private static String simpleAnn(@NotNull final String desc) {
        final String type = Type.getType(desc).getClassName();
        final int i = type.lastIndexOf('.');
        return (i >= 0) ? type.substring(i + 1) : type;
    }
}
