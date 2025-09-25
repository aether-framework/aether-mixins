package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.ClassWork;
import de.splatgames.aether.mixins.bytecode.weaver.asm.FinalNameRegistry;
import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.ShadowMap;
import de.splatgames.aether.mixins.bytecode.weaver.asm.shadow.ShadowRewriter;
import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.InjectionSpec;
import de.splatgames.aether.mixins.bytecode.weaver.asm.spec.RedirectSpec;
import de.splatgames.aether.mixins.bytecode.weaver.hook.ResolvedHook;
import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import de.splatgames.aether.mixins.core.weaver.spi.WeaveRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.objectweb.asm.Opcodes.ASM9;

/**
 * Pre-merges mixin hook methods into the target class before weaving proceeds.
 *
 * <p>This {@link ClassVisitor} ensures that every hook method referenced by either an
 * injection or a redirect is present on the target class. The pre-merge step copies
 * the hook method body from the mixin into the target class (as a private method),
 * performs shadow rewrites within that method body, and handles {@code @Unique}
 * renaming to avoid signature collisions.</p>
 *
 * <h2>Responsibilities</h2>
 * <ol>
 *   <li>Collect all {@link ResolvedHook hooks} referenced by {@link InjectionSpec} and {@link RedirectSpec}.</li>
 *   <li>For each hook:
 *     <ol>
 *       <li>Load the mixin class and locate the hook method implementation.</li>
 *       <li>Load the target class to build a {@link ShadowMap}, then rewrite shadow
 *           field/method instructions in the copied body via {@link ShadowRewriter}.</li>
 *       <li>Honor {@code @Unique}: if the target already declares the same name+desc,
 *           deterministically rename the copied method and record the mapping in
 *           {@link FinalNameRegistry}.</li>
 *       <li>Emit the copied (and possibly renamed) method as a {@code private} member on the target.</li>
 *     </ol>
 *   </li>
 * </ol>
 *
 * <h2>Access flags and annotations</h2>
 * <ul>
 *   <li>Copied methods are emitted as {@code private} and retain {@code synchronized}, {@code varargs},
 *       {@code bridge}, and {@code synthetic} flags where present. Abstract/native hooks are rejected.</li>
 *   <li>Mixin-only annotations (e.g., {@code @Inject}, {@code @Redirect}, {@code @Shadow}, {@code @Unique})
 *       are stripped from the copied method.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>The visitor carries per-instance, per-class state and is not thread-safe.</p>
 *
 * @author Erik Pförtner
 * @see ShadowMap
 * @see ShadowRewriter
 * @see FinalNameRegistry
 * @see InjectionSpec
 * @see RedirectSpec
 * @since 0.2.0
 */
public final class PremergeInstanceHooksAdapter extends ClassVisitor {

    /**
     * Internal JVM name (slash-separated) of the <em>target</em> class being visited.
     */
    private final String targetOwner;
    /**
     * Aggregated work describing injections and redirects for the target class.
     */
    private final ClassWork work;
    /**
     * Weave request providing class bytes and runtime configuration.
     */
    private final WeaveRequest request;
    /**
     * Diagnostic sink for errors and warnings encountered during pre-merge.
     */
    private final ConfigProblems problems;

    /**
     * Set of method signatures (keyed as {@code name+desc}) already present on the target,
     * populated during {@link #visitMethod(int, String, String, String, String[])}.
     */
    private final Set<String> existing = new HashSet<>();

    /**
     * Set of field signatures (keyed as {@code name+desc}) already present on the target,
     * populated during {@link #visitField(int, String, String, String, Object)}.
     */
    private final Set<String> existingFields = new HashSet<>();

    /**
     * Creates a new pre-merge adapter that copies hook methods from mixins into the target owner.
     *
     * @param api         ASM API level
     * @param cv          downstream class visitor; must not be {@code null}
     * @param targetOwner internal JVM name of the target class; must not be {@code null}
     * @param work        weaving work describing hooks for this target; must not be {@code null}
     * @param request     weave request providing class bytes; must not be {@code null}
     * @param problems    diagnostic sink; must not be {@code null}
     */
    public PremergeInstanceHooksAdapter(final int api,
                                        @NotNull final ClassVisitor cv,
                                        @NotNull final String targetOwner,
                                        @NotNull final ClassWork work,
                                        @NotNull final WeaveRequest request,
                                        @NotNull final ConfigProblems problems) {
        super(api, cv);
        this.targetOwner = targetOwner;
        this.work = work;
        this.request = request;
        this.problems = problems;
    }

    /**
     * Records existing methods on the target class.
     *
     * @param access the method's access flags (see {@link Opcodes}). This parameter also indicates if
     *               the method is synthetic and/or deprecated.
     * @param name   the method's name.
     * @param desc   the method's descriptor (see {@link org.objectweb.asm.Type Type}).
     * @param sig    the method's signature. May be {@literal null} if the method parameters,
     *               return type and exceptions do not use generic types.
     * @param ex     the internal names of the method's exception classes (see {@link org.objectweb.asm.Type#getInternalName() Type.getInternalName}). May be {@literal null}.
     * @return a visitor to visit the method's code, annotations and attributes, or {@literal null}
     * if this class visitor is not interested in visiting this method.
     */
    @Override
    @Nullable
    public MethodVisitor visitMethod(final int access,
                                     final String name,
                                     final String desc,
                                     final String sig,
                                     final String[] ex) {
        this.existing.add(name + desc);
        return super.visitMethod(access, name, desc, sig, ex);
    }

    @Override
    public FieldVisitor visitField(final int access,
                                   final String name,
                                   final String desc,
                                   final String sig,
                                   final Object value) {
        this.existingFields.add(name + desc);
        return super.visitField(access, name, desc, sig, value);
    }

    /**
     * At class end, enumerates every hook and ensures a concrete method exists on the target.
     *
     * <p>Both injection and redirect specs are considered. For each referenced hook, the
     * method body is copied from the mixin and adjusted (shadows + unique rename) as needed.</p>
     */
    @Override
    public void visitEnd() {
        // Inject instance (and static) hook methods if needed.
        Stream.concat(
                this.work.getInjects().values().stream().flatMap(List::stream).map(InjectionSpec::hook),
                this.work.getRedirects().values().stream().flatMap(List::stream).map(RedirectSpec::hook)
        ).forEach(this::ensureMethodPresent);

        super.visitEnd();
    }

    /**
     * Ensures a single hook method exists on the target, copying and rewriting it if necessary.
     *
     * <p>Processing steps:</p>
     * <ol>
     *   <li>Load mixin class and locate the source hook method.</li>
     *   <li>Load target class to construct a {@link ShadowMap} and rewrite shadow accesses in the method body.</li>
     *   <li>Apply {@code @Unique} collision handling (deterministic rename + registry record) if target already has {@code name+desc}.</li>
     *   <li>Emit a private method on the target with compatible flags; strip mixin-only annotations, and copy the body.</li>
     * </ol>
     *
     * @param hook the resolved hook (owner/name/desc) that must be present on the target
     */
    private void ensureMethodPresent(@NotNull final ResolvedHook hook) {
        final String ctx = "premerge/" + this.targetOwner;
        final String nameDesc = hook.name() + hook.desc();

        // 1) Load mixin class bytes
        final byte[] mixinBytes;
        try {
            mixinBytes = this.request.source().getClassBytes(hook.owner());
        } catch (Exception e) {
            this.problems.error(ctx, "Failed to read mixin owner '" + hook.owner() + "': " + e.getMessage());
            return;
        }
        if (mixinBytes == null) {
            this.problems.error(ctx, "Mixin owner not found: " + hook.owner());
            return;
        }

        // 2) Parse mixin class
        final ClassNode mixinNode = new ClassNode(ASM9);
        new ClassReader(mixinBytes).accept(mixinNode, 0);

        // 3) Locate source hook method in mixin
        final MethodNode src = mixinNode.methods.stream()
                .filter(m -> m.name.equals(hook.name()) && m.desc.equals(hook.desc()))
                .findFirst().orElse(null);
        if (src == null) {
            this.problems.error(ctx, "Hook method not found in mixin: " + hook.owner() + "." + hook.name() + hook.desc());
            return;
        }

        // 4) Load & parse target class (for shadow resolution)
        final byte[] targetBytes;
        try {
            targetBytes = this.request.source().getClassBytes(this.targetOwner);
        } catch (Exception e) {
            this.problems.error(ctx, "Failed to read target class '" + this.targetOwner + "': " + e.getMessage());
            return;
        }
        if (targetBytes == null) {
            this.problems.error(ctx, "Target class not found: " + this.targetOwner);
            return;
        }
        final ClassNode targetNode = new ClassNode(ASM9);
        new ClassReader(targetBytes).accept(targetNode, 0);

        // 5) Build ShadowMap from mixin+target and rewrite shadow usages inside the method body
        final ShadowMap shadowMap = ShadowMap.from(
                mixinNode, targetNode, this.problems, "shadow/" + this.targetOwner + "/" + hook.owner()
        );

        // --- Hoist @Unique FIELDS referenced by the hook body ---
        final String UNIQUE_DESC_ANN = "Lde/splatgames/aether/mixins/core/api/Unique;";

        for (var insn = src.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof FieldInsnNode fin && fin.owner.equals(mixinNode.name)) {
                // Find the referenced field on the mixin
                final FieldNode mixinField = mixinNode.fields.stream()
                        .filter(f -> f.name.equals(fin.name) && f.desc.equals(fin.desc))
                        .findFirst().orElse(null);
                if (mixinField == null) {
                    // Not declared on mixin; ShadowRewriter will sanity-check.
                    continue;
                }

                final boolean isUniqueField =
                        (mixinField.visibleAnnotations != null && mixinField.visibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC_ANN.equals(a.desc))) ||
                                (mixinField.invisibleAnnotations != null && mixinField.invisibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC_ANN.equals(a.desc)));

                if (!isUniqueField) {
                    // Only hoist @Unique fields here; regular fields are expected to be shadowed or rejected elsewhere.
                    continue;
                }

                // Decide final field name; handle collisions deterministically (same scheme as methods)
                String finalFieldName = mixinField.name;
                if (this.existingFields.contains(mixinField.name + mixinField.desc)) {
                    finalFieldName = mixinField.name + "$am$" + Integer.toHexString((mixinNode.name + mixinField.name + mixinField.desc).hashCode());
                    FinalNameRegistry.register(this.targetOwner, mixinNode.name, mixinField.name, mixinField.desc, finalFieldName);
                }

                // If we already emitted the field under final name, just rewrite owner/name at the callsite
                if (this.existingFields.contains(finalFieldName + mixinField.desc)) {
                    fin.owner = this.targetOwner;
                    fin.name = finalFieldName;
                    continue;
                }

                // Emit field onto target; make it private, preserve useful flags (e.g., volatile, final)
                final int fieldAccess =
                        (mixinField.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) |
                                Opcodes.ACC_PRIVATE |
                                (mixinField.access & (Opcodes.ACC_VOLATILE | Opcodes.ACC_FINAL | Opcodes.ACC_TRANSIENT | Opcodes.ACC_SYNTHETIC));

                // Copy field; strip mixin-only annotations
                final var fv = super.visitField(fieldAccess, finalFieldName, mixinField.desc, mixinField.signature, mixinField.value);
                // We don’t propagate @Unique/@Inject/@Redirect/@Shadow annotations to the target
                // (ASM’s FieldNode -> FieldVisitor copy is manual here; we skip annotations entirely.)
                if (fv != null) {
                    fv.visitEnd();
                }

                this.existingFields.add(finalFieldName + mixinField.desc);

                // Rewrite field ref in hook body to target owner + final name
                fin.owner = this.targetOwner;
                fin.name = finalFieldName;
            }
        }

        // hoist @Unique helpers referenced by the hook body
        for (var insn = src.instructions.getFirst(); insn != null; insn = insn.getNext()) {
            if (insn instanceof MethodInsnNode min && min.owner.equals(mixinNode.name)) {
                // Find the referenced method on the mixin
                final MethodNode helper = mixinNode.methods.stream()
                        .filter(m -> m.name.equals(min.name) && m.desc.equals(min.desc))
                        .findFirst().orElse(null);
                if (helper == null) {
                    // Not a mixin-defined method → leave; ShadowRewriter will sanity-check
                    continue;
                }

                final boolean isUniqueHelper =
                        (helper.visibleAnnotations != null && helper.visibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC_ANN.equals(a.desc))) ||
                                (helper.invisibleAnnotations != null && helper.invisibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC_ANN.equals(a.desc)));

                if (!isUniqueHelper) {
                    // Only hoist @Unique helpers here
                    continue;
                }

                // Decide final helper name; handle collision like for hooks
                String helperFinalName = helper.name;
                if (this.existing.contains(helper.name + helper.desc)) {
                    helperFinalName = helper.name + "$am$" + Integer.toHexString((mixinNode.name + helper.name + helper.desc).hashCode());
                    FinalNameRegistry.register(this.targetOwner, mixinNode.name, helper.name, helper.desc, helperFinalName);
                }

                // If we've already emitted the helper under final name, just rewrite callsite and continue
                if (this.existing.contains(helperFinalName + helper.desc)) {
                    min.owner = this.targetOwner;
                    min.name  = helperFinalName;
                    continue;
                }

                // Emit @Unique helper onto target as private (keep useful flags); reject abstract/native
                final int helperAccess =
                        (helper.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) |
                                Opcodes.ACC_PRIVATE |
                                (helper.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_VARARGS | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC));

                if ((helper.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
                    this.problems.error("premerge/" + this.targetOwner,
                            "@Unique helper must be concrete (no abstract/native): " +
                                    mixinNode.name + "." + helper.name + helper.desc);
                    return;
                }

                // Copy helper, rewrite shadows inside the helper too, strip mixin-only annotations, then emit
                final MethodNode helperCopy = new MethodNode(
                        helperAccess, helperFinalName, helper.desc, helper.signature,
                        (helper.exceptions == null ? null : helper.exceptions.toArray(String[]::new))
                );
                helper.accept(helperCopy);

                // Rewrite shadows / mixin-owner refs inside helper copy as well
                ShadowRewriter.rewriteMethodBody(
                        helperCopy, mixinNode.name, this.targetOwner, shadowMap, mixinNode
                );

                if (helperCopy.visibleAnnotations != null) {
                    helperCopy.visibleAnnotations.removeIf(a ->
                            UNIQUE_DESC_ANN.equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Inject;".equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Redirect;".equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Shadow;".equals(a.desc)
                    );
                }
                if (helperCopy.invisibleAnnotations != null) {
                    helperCopy.invisibleAnnotations.removeIf(a ->
                            UNIQUE_DESC_ANN.equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Inject;".equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Redirect;".equals(a.desc) ||
                                    "Lde/splatgames/aether/mixins/core/api/Shadow;".equals(a.desc)
                    );
                }

                final MethodVisitor helperMV = super.visitMethod(
                        helperAccess, helperFinalName, helper.desc, helper.signature,
                        (helper.exceptions == null ? null : helper.exceptions.toArray(String[]::new))
                );
                helperCopy.accept(helperMV);
                this.existing.add(helperFinalName + helper.desc);

                // Rewrite call site in the hook body to target owner + final helper name
                min.owner = this.targetOwner;
                min.name  = helperFinalName;
            }
        }

        ShadowRewriter.rewriteMethodBody(
                src,
                mixinNode.name,
                this.targetOwner,
                shadowMap,
                mixinNode
        );

        // 6) Handle @Unique (visible or invisible)
        final String UNIQUE_DESC = "Lde/splatgames/aether/mixins/core/api/Unique;";
        final boolean isUnique =
                (src.visibleAnnotations != null && src.visibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc))) ||
                        (src.invisibleAnnotations != null && src.invisibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc)));

        // 7) Decide insertion/rename policy
        String finalName = hook.name();
        if (this.existing.contains(nameDesc)) {
            finalName = hook.name() + "$am$" + Integer.toHexString((hook.owner() + hook.name() + hook.desc()).hashCode());
            // IMPORTANT: register per mixin-owner to disambiguate multiple mixins with same hook name+desc
            FinalNameRegistry.register(this.targetOwner, hook.owner(), hook.name(), hook.desc(), finalName);
        }

        // 8) Compute final access (private body copy; keep useful flags)
        final int access =
                (src.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) |
                        Opcodes.ACC_PRIVATE |
                        (src.access & (Opcodes.ACC_SYNCHRONIZED | Opcodes.ACC_VARARGS | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC));

        // Disallow abstract/native hooks – they must be concrete and copyable.
        if ((src.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) != 0) {
            this.problems.error(ctx, "Hook method must be concrete (no abstract/native): " +
                    hook.owner() + "." + hook.name() + hook.desc());
            return;
        }

        // 9) Create target method and strip mixin-only annotations from the copy
        final MethodVisitor mv = super.visitMethod(
                access,
                finalName, hook.desc(), src.signature,
                (src.exceptions == null ? null : src.exceptions.toArray(String[]::new))
        );

        // Remove Inject/Redirect/Shadow/Unique annotations from visible & invisible sets
        if (src.visibleAnnotations != null) {
            src.visibleAnnotations.removeIf(a ->
                    UNIQUE_DESC.equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Inject;".equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Redirect;".equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Shadow;".equals(a.desc)
            );
        }
        if (src.invisibleAnnotations != null) {
            src.invisibleAnnotations.removeIf(a ->
                    UNIQUE_DESC.equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Inject;".equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Redirect;".equals(a.desc) ||
                            "Lde/splatgames/aether/mixins/core/api/Shadow;".equals(a.desc)
            );
        }

        // 10) Write method body to target
        src.accept(mv);

        // 11) Register existence for this class visit
        this.existing.add(finalName + hook.desc());
    }
}
