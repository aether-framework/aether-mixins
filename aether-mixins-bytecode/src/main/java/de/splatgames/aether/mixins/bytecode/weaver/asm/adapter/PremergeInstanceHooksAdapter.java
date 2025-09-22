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
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
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
     * Records existing methods on the target class to detect collisions with copied hooks.
     *
     * <p>Each visited method's {@code name+desc} is stored in {@link #existing} to determine
     * whether an incoming hook requires renaming under {@code @Unique} rules.</p>
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

        ShadowRewriter.rewriteMethodBody(
                src,
                mixinNode.name,
                this.targetOwner,
                shadowMap
        );

        // 6) Handle @Unique (visible or invisible)
        final String UNIQUE_DESC = "Lde/splatgames/aether/mixins/core/api/Unique;";
        final boolean isUnique =
                (src.visibleAnnotations != null && src.visibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc))) ||
                        (src.invisibleAnnotations != null && src.invisibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc)));

        // 7) Decide insertion/rename policy
        String finalName = hook.name();
        if (this.existing.contains(nameDesc)) {
            if (!isUnique) {
                // Target already has same signature and method is not @Unique → skip
                return;
            }
            // @Unique: rename deterministically and register mapping for downstream lookup
            finalName = hook.name() + "$am$" + Integer.toHexString((hook.owner() + hook.name() + hook.desc()).hashCode());
            FinalNameRegistry.register(this.targetOwner, hook.name(), hook.desc(), finalName);
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
