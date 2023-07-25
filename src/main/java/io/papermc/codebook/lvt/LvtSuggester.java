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

package io.papermc.codebook.lvt;

import static dev.denwav.hypo.asm.HypoAsmUtil.toJvmType;
import static org.objectweb.asm.Type.getType;

import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.types.JvmType;
import java.io.IOException;
import java.util.Set;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LvtSuggester {

    private LvtSuggester() {}

    public static String suggestName(
            final HypoContext context,
            final LocalVariableNode lvt,
            final MethodNode node,
            final Set<String> scopedNames)
            throws IOException {
        @Nullable VarInsnNode assignmentNode = null;
        for (final AbstractInsnNode insn : node.instructions) {
            final int op = insn.getOpcode();
            if (op < Opcodes.ISTORE || op > Opcodes.ASTORE) {
                continue;
            }

            final var varInsn = (VarInsnNode) insn;
            if (varInsn.var == lvt.index) {
                assignmentNode = varInsn;
                break;
            }
        }

        if (assignmentNode != null) {
            final @Nullable String suggestedName = suggestNameFromFirstAssignment(context, assignmentNode);
            if (suggestedName != null) {
                return determineFinalName(suggestedName, scopedNames);
            }
        }

        // we couldn't determine a name from the assignment, so determine a name from the type
        final JvmType lvtType = toJvmType(getType(lvt.desc));
        return determineFinalName(LvtTypeSuggester.suggestNameFromType(context, lvtType), scopedNames);
    }

    private static String determineFinalName(final String suggestedName, final Set<String> scopedNames) {
        final String shortName = shortenName(suggestedName);

        final String name;
        if (JAVA_KEYWORDS.contains(shortName)) {
            name = "_" + shortName;
        } else {
            name = shortName;
        }

        if (!scopedNames.contains(name)) {
            return name;
        }

        int counter = 1;
        while (true) {
            final String nextSuggestedName = name + counter;
            if (!scopedNames.contains(nextSuggestedName)) {
                return nextSuggestedName;
            }
            counter++;
        }
    }

    private static @Nullable String suggestNameFromFirstAssignment(final HypoContext context, final VarInsnNode varInsn)
            throws IOException {
        final AbstractInsnNode prev = varInsn.getPrevious();
        final int op = prev.getOpcode();
        if (op != Opcodes.INVOKESTATIC && op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKEINTERFACE) {
            return null;
        }

        final MethodInsnNode methodInsnNode = (MethodInsnNode) prev;
        final String name = methodInsnNode.name;

        final @Nullable ClassData ownerClass = context.getContextProvider().findClass(methodInsnNode.owner);
        if (ownerClass != null && ownerClass.kind() == ClassKind.RECORD) {
            return LvtAssignmentSuggester.suggestNameFromRecord(name);
        } else {
            return LvtAssignmentSuggester.suggestNameFromAssignment(name, methodInsnNode);
        }
    }

    private static String shortenName(final String name) {
        return switch (name) {
            case "context" -> "ctx";
            default -> name;
        };
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract",
            "continue",
            "for",
            "new",
            "switch",
            "assert",
            "default",
            "goto",
            "package",
            "synchronized",
            "boolean",
            "do",
            "if",
            "private",
            "this",
            "break",
            "double",
            "implements",
            "protected",
            "throw",
            "byte",
            "else",
            "import",
            "public",
            "throws",
            "case",
            "enum",
            "instanceof",
            "return",
            "transient",
            "catch",
            "extends",
            "int",
            "short",
            "try",
            "char",
            "final",
            "interface",
            "static",
            "void",
            "class",
            "finally",
            "long",
            "strictfp",
            "volatile",
            "const",
            "float",
            "native",
            "super",
            "while",
            "exports",
            "module",
            "non-sealed",
            "open",
            "opens",
            "permits",
            "provides",
            "record",
            "sealed",
            "transitive",
            "uses",
            "var",
            "with",
            "yield");
}
