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

import static dev.denwav.hypo.model.data.MethodDescriptor.parseDescriptor;
import static io.papermc.codebook.lvt.LvtUtil.toJvmType;

import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.MethodDescriptor;
import dev.denwav.hypo.model.data.types.JvmType;
import java.io.IOException;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LvtSuggester {

    private LvtSuggester() {}

    public static String suggestName(
            final HypoContext context, final LocalVariableNode lvt, final Set<String> scopedNames) throws IOException {
        @Nullable VarInsnNode assignmentNode = null;
        AbstractInsnNode insn = lvt.start;
        while (insn.getNext() != null) {
            insn = insn.getNext();
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
        final JvmType lvtType = toJvmType(lvt.desc);
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

        final @Nullable ClassData owner = context.getContextProvider().findClass(methodInsnNode.owner);
        if (owner == null) {
            return null;
        }
        final @Nullable MethodData method =
                findMethod(owner, methodInsnNode.name, parseDescriptor(methodInsnNode.desc));
        if (method == null) {
            return null;
        }

        return LvtAssignmentSuggester.suggestNameFromAssignment(context, owner, method, methodInsnNode);
    }

    private static @Nullable MethodData findMethod(
            final @Nullable ClassData data, final String name, final MethodDescriptor desc) throws IOException {
        if (data == null) {
            return null;
        }

        {
            final @Nullable MethodData method = data.method(name, desc);
            if (method != null) {
                return method;
            }
        }

        final @Nullable ClassData superClass = data.superClass();
        if (superClass != null) {
            final @Nullable MethodData method = findMethod(superClass, name, desc);
            if (method != null) {
                return method;
            }
        }

        for (final ClassData anInterface : data.interfaces()) {
            final @Nullable MethodData method = findMethod(anInterface, name, desc);
            if (method != null) {
                return method;
            }
        }

        return null;
    }

    private static String shortenName(final String name) {
        if (name.equals("context")) {
            return "ctx";
        }
        return name;
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
