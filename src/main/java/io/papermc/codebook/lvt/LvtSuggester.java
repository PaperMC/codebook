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

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.MethodDescriptor;
import dev.denwav.hypo.model.data.types.JvmType;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class LvtSuggester {

    private final HypoContext context;
    private final LvtAssignmentSuggester assignmentSuggester;

    public LvtSuggester(final HypoContext context, final Map<String, AtomicInteger> missedNameSuggestions)
            throws IOException {
        this.context = context;
        this.assignmentSuggester = new LvtAssignmentSuggester(context, missedNameSuggestions);
    }

    public String suggestName(final MethodNode node, final LocalVariableNode lvt, final Set<String> scopedNames)
            throws IOException {
        @Nullable VarInsnNode assignmentNode = null;
        // `insn` could represent the first instruction, so check if there actually is a previous instruction
        if (lvt.start.getPrevious() != null) {
            // In most cases the store instruction for a new local variable will happen directly before the label which
            // marks the variable's start.
            // We do this quick check before checking all instructions as a fallback.
            // The most common exception is parameters, which start at 0
            final AbstractInsnNode prev = lvt.start.getPrevious();
            final int op = prev.getOpcode();
            if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
                final var varInsn = (VarInsnNode) prev;
                if (varInsn.var == lvt.index) {
                    assignmentNode = varInsn;
                }
            }
        }

        if (assignmentNode == null) {
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
        }

        if (assignmentNode != null) {
            final @Nullable String suggestedName = this.suggestNameFromFirstAssignment(assignmentNode);
            if (suggestedName != null) {
                return determineFinalName(suggestedName, scopedNames);
            }
        }

        // we couldn't determine a name from the assignment, so determine a name from the type
        final JvmType lvtType = toJvmType(lvt.desc);
        return determineFinalName(LvtTypeSuggester.suggestNameFromType(this.context, lvtType), scopedNames);
    }

    public static String determineFinalName(final String suggestedName, final Set<String> scopedNames) {
        final String shortName = shortenName(suggestedName);

        final String name;
        if (JAVA_KEYWORDS.contains(shortName)) {
            name = "_" + shortName;
        } else {
            name = shortName;
        }

        if (scopedNames.add(name)) {
            return name;
        }

        int counter = 1;
        while (true) {
            final String nextSuggestedName = name + counter;
            if (scopedNames.add(nextSuggestedName)) {
                return nextSuggestedName;
            }
            counter++;
        }
    }

    private @Nullable String suggestNameFromFirstAssignment(final VarInsnNode varInsn) throws IOException {
        final AbstractInsnNode prev = varInsn.getPrevious();
        final int op = prev.getOpcode();
        if (op != Opcodes.INVOKESTATIC && op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKEINTERFACE) {
            return null;
        }

        final MethodInsnNode methodInsnNode = (MethodInsnNode) prev;

        final @Nullable ClassData owner = this.context.getContextProvider().findClass(methodInsnNode.owner);
        if (owner == null) {
            return null;
        }
        final @Nullable MethodData method =
                findMethod(owner, methodInsnNode.name, parseDescriptor(methodInsnNode.desc));
        if (method == null) {
            return null;
        }

        return this.assignmentSuggester.suggestNameFromAssignment(
                (AsmClassData) owner, (AsmMethodData) method, methodInsnNode);
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
