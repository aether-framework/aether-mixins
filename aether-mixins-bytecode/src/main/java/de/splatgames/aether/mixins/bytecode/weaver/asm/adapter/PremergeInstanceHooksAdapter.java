package de.splatgames.aether.mixins.bytecode.weaver.asm.adapter;

import de.splatgames.aether.mixins.bytecode.weaver.asm.ClassWork;
import de.splatgames.aether.mixins.bytecode.weaver.asm.FinalNameRegistry;
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
        final String nameDesc = hook.name() + hook.desc();

        // Load mixin class bytes
        final byte[] mixinBytes;
        try {
            mixinBytes = this.request.source().getClassBytes(hook.owner());
        } catch (Exception e) {
            this.problems.error("premerge/" + this.targetOwner, "Failed to read mixin owner '" +
                    hook.owner() + "': " + e.getMessage());
            return;
        }
        if (mixinBytes == null) {
            this.problems.error("premerge/" + this.targetOwner, "Mixin owner not found: " + hook.owner());
            return;
        }

        final ClassNode mixinNode = new ClassNode(ASM9);
        new ClassReader(mixinBytes).accept(mixinNode, 0);

        final MethodNode src = mixinNode.methods.stream()
                .filter(m -> m.name.equals(hook.name()) && m.desc.equals(hook.desc()))
                .findFirst().orElse(null);
        if (src == null) {
            this.problems.error("premerge/" + targetOwner, "Hook method not found in mixin: " +
                    hook.owner() + "." + hook.name() + hook.desc());
            return;
        }

        // Detect @Unique on the source method (visible or invisible)
        final String UNIQUE_DESC = "Lde/splatgames/aether/mixins/core/api/Unique;";
        final boolean isUnique =
                (src.visibleAnnotations != null && src.visibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc))) ||
                        (src.invisibleAnnotations != null && src.invisibleAnnotations.stream().anyMatch(a -> UNIQUE_DESC.equals(a.desc)));

        // Decide insertion/rename policy
        String finalName = hook.name();
        if (this.existing.contains(nameDesc)) {
            if (!isUnique) {
                // Target already has same signature and method is not @Unique → skip
                return;
            }
            // @Unique: rename deterministically
            finalName = hook.name() + "$am$" + Integer.toHexString((hook.owner() + hook.name() + hook.desc()).hashCode());
            FinalNameRegistry.register(this.targetOwner, hook.name(), hook.desc(), finalName);
        } else if (this.existing.contains(finalName + hook.desc())) {
            // Conservative: if same simple name collides for other reasons, also rename when @Unique
            if (isUnique) {
                finalName = hook.name() + "$am$" + Integer.toHexString((hook.owner() + hook.name() + hook.desc()).hashCode());
                FinalNameRegistry.register(this.targetOwner, hook.name(), hook.desc(), finalName);
            } else {
                // Normally shouldn't happen since we checked name+desc above, but guard anyway
                return;
            }
        }

        final int access =
                (src.access & ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PROTECTED)) |
                        Opcodes.ACC_PRIVATE |
                        (src.access & Opcodes.ACC_SYNCHRONIZED);

        final MethodVisitor mv = super.visitMethod(
                access,
                finalName, hook.desc(), src.signature,
                (src.exceptions == null ? null : src.exceptions.toArray(String[]::new))
        );

        if (src.visibleAnnotations != null) {
            src.visibleAnnotations.removeIf(a -> UNIQUE_DESC.equals(a.desc)
                    || "Lde/splatgames/aether/mixins/core/api/Inject;".equals(a.desc)
                    || "Lde/splatgames/aether/mixins/core/api/Redirect;".equals(a.desc)
                    || "Lde/splatgames/aether/mixins/core/api/Shadow;".equals(a.desc));
        }
        src.accept(mv);

        this.existing.add(finalName + hook.desc());
    }
}
