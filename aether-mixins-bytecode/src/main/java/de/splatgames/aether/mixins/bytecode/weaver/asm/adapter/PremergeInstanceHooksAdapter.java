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

public final class PremergeInstanceHooksAdapter extends ClassVisitor {
    private final String targetOwner;
    private final ClassWork work;
    private final WeaveRequest request;
    private final ConfigProblems problems;

    private final Set<String> existing = new HashSet<>();

    public PremergeInstanceHooksAdapter(final int api, final ClassVisitor cv, final String targetOwner,
                                        final ClassWork work, final WeaveRequest request, final ConfigProblems problems) {
        super(api, cv);
        this.targetOwner = targetOwner;
        this.work = work;
        this.request = request;
        this.problems = problems;
    }

    @Override
    @Nullable
    public MethodVisitor visitMethod(final int access, final String name, final String desc,
                                     final String sig, final String[] ex) {
        this.existing.add(name + desc);
        return super.visitMethod(access, name, desc, sig, ex);
    }

    @Override
    public void visitEnd() {
        // Inject instance hook methods if needed.
        Stream.concat(
                        this.work.getInjects().values().stream().flatMap(List::stream).map(InjectionSpec::hook),
                        this.work.getRedirects().values().stream().flatMap(List::stream).map(RedirectSpec::hook)
                ).filter(h -> h.invocation().isInstance())
                .forEach(this::ensureMethodPresent);

        super.visitEnd();
    }

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
                shadowMap,
                this.problems,
                "shadow-use/" + this.targetOwner + "/" + hook.name() + hook.desc()
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
            // @Unique: rename deterministically
            finalName = hook.name() + "$am$" + Integer.toHexString((hook.owner() + hook.name() + hook.desc()).hashCode());
            FinalNameRegistry.register(this.targetOwner, hook.name(), hook.desc(), finalName);
        }

        // 8) Compute final access (private synthetic body copy; keep synchronized flag if present)
        final int access =
                (src.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) |
                        Opcodes.ACC_PRIVATE |
                        (src.access & Opcodes.ACC_SYNCHRONIZED);

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

        // 11) Register existence
        this.existing.add(finalName + hook.desc());
    }
}
