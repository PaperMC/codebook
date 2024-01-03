package io.papermc.codebook.lvt.suggestion;

import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import java.util.function.IntPredicate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public class FluentGetterSuggester implements LvtSuggester {

    // 3 instructions, load "this" local var, getfield, return - TODO maybe if there is a CAST,
    private static final IntPredicate[] OPCODES_IN_ORDER = new IntPredicate[] {
            i -> i == Opcodes.ALOAD, i -> i == Opcodes.GETFIELD, i -> i >= Opcodes.IRETURN && i <= Opcodes.RETURN};

    @Override
    public @Nullable String suggestFromMethod(final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container) throws IOException {
        // I think it's best to only work with primitive types here, as other types should already have names
        // and this dramatically cuts down on the number of methods analyzed because we aren't filtering by
        // method name
        if (!(call.data().returnType() instanceof PrimitiveType)
                || !call.data().params().isEmpty()) {
            return null;
        }
        int opcodeIndex = 0;
        final InsnList instructions = call.node().instructions;
        if (instructions.size() == 0) {
            return null;
        }
        for (final AbstractInsnNode methodInsn : instructions) {
            if (methodInsn.getOpcode() == -1) {
                continue;
            }
            if (opcodeIndex == OPCODES_IN_ORDER.length) {
                break; // matched the correct order
            }
            if (OPCODES_IN_ORDER[opcodeIndex].test(methodInsn.getOpcode())) {
                opcodeIndex++;
            } else {
                return null;
            }
        }
        if (call.data().isStatic()) { // limit static matches
            if ("java/lang/System".equals(insn.node().owner) && "currentTimeMillis".equals(insn.node().name)) {
                return "currentTimeMillis";
            }
        } else {
            return call.data().name();
        }
        return null;
    }
}
