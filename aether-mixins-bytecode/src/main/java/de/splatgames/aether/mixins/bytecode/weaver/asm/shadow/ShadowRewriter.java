package de.splatgames.aether.mixins.bytecode.weaver.asm.shadow;

import de.splatgames.aether.mixins.core.config.problems.ConfigProblems;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class ShadowRewriter {
    private ShadowRewriter() {
        // utility class, not instantiable
    }

    public static void rewriteMethodBody(@NotNull final MethodNode method,
                                         @NotNull final String mixinOwner,
                                         @NotNull final String targetOwner,
                                         @NotNull final ShadowMap map,
                                         @NotNull final ConfigProblems problems,
                                         @NotNull final String contextPath) {

        for (AbstractInsnNode insn = method.instructions.getFirst();
             insn != null;
             insn = insn.getNext()) {

            if (insn instanceof FieldInsnNode fin && fin.owner.equals(mixinOwner)) {
                ShadowBinding binding = map.field(fin.name, fin.desc);
                if (binding == null) {
                    continue;
                }

                if (!binding.isResolved() && !binding.isOptional()) {
                    problems.error(contextPath, "Unresolved @Shadow field access: " + fin.name + fin.desc);
                } else if (binding.isResolved()) {
                    fin.owner = targetOwner;
                    fin.name = binding.getStrippedName();
                }
            }

            if (insn instanceof MethodInsnNode min && min.owner.equals(mixinOwner)) {
                ShadowBinding binding = map.method(min.name, min.desc);
                if (binding == null) {
                    continue;
                }

                if (!binding.isResolved() && !binding.isOptional()) {
                    problems.error(contextPath, "Unresolved @Shadow method access: " + min.name + min.desc);
                } else if (binding.isResolved()) {
                    min.owner = targetOwner;
                    min.name = binding.getStrippedName();
                }
            }
        }
    }
}
