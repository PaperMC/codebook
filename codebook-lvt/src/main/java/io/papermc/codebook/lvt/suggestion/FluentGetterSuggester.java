/*
 * codebook is a remapper utility for the PaperMC project.
 *
 * Copyright (c) 2023 Kyle Wood (DenWav)
 *                    Contributors
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 3 only, no later versions.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301
 * USA
 */

package io.papermc.codebook.lvt.suggestion;

import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntPredicate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;

public class FluentGetterSuggester implements LvtSuggester {

    private static final Set<String> ignored = Set.of(
            "byteValue",
            "shortValue",
            "intValue",
            "longValue",
            "floatValue",
            "doubleValue",
            "booleanValue",
            "charValue",
            "get");

    // 3 instructions, load "this" local var, getfield, return - TODO maybe if there is a CAST,
    private static final IntPredicate[] OPCODES_IN_ORDER = new IntPredicate[] {
        i -> i == Opcodes.ALOAD, i -> i == Opcodes.GETFIELD, i -> i >= Opcodes.IRETURN && i <= Opcodes.RETURN
    };

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container)
            throws IOException {
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
            final String name = call.data().name();
            if (ignored.contains(name)) {
                return null;
            }
            final @Nullable String forLoopAdjustedName = SingleVerbSuggester.handleForLoop(name, insn, "min", "max");
            return Objects.requireNonNullElse(forLoopAdjustedName, name);
        }
        return null;
    }
}
