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
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import org.cadixdev.bombe.type.FieldType;
import org.cadixdev.bombe.type.MethodDescriptor;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public final class LvtAssignmentSuggester {

    private LvtAssignmentSuggester() {}

    public static @Nullable String suggestNameFromAssignment(
            final @Nullable HypoContext context, final String methodName, final MethodInsnNode insn)
            throws IOException {
        @Nullable String suggested = suggestGeneric(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromGetter(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromVerbBoolean(methodName, insn);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromSingleWorldVerbBoolean(context, methodName, insn);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromAs(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromNew(methodName, insn);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromRead(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromLine(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromStrings(methodName, insn);
        return suggested;
    }

    public static @Nullable String suggestGeneric(final String methodName) {
        return switch (methodName) {
            case "hashCode" -> "hashCode";
            case "size" -> "size";
            case "length" -> "len";
            case "freeze" -> "frozen";
            default -> null;
        };
    }

    public static String suggestNameFromRecord(final String methodName) {
        // method exists in case we want to make additional changes later
        return methodName;
    }

    private static @Nullable String suggestNameFromGetter(final String methodName) {
        if (!methodName.startsWith("get") || methodName.equals("get")) {
            // If the method isn't `get<Thing>` - or if the method is just `get`
            return null;
        }

        int index = 3;
        if (methodName.startsWith("getOrCreate")) {
            if (methodName.equals("getOrCreate")) {
                return null;
            }
            index = 11;
        }

        final String baseName = methodName.substring(index);

        if (Character.isUpperCase(baseName.charAt(0))) {
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        } else {
            // if the name doesn't follow the typical `getName` scheme we can't be confident it's
            // really a "getter" method, so don't use it for a name
            return null;
        }
    }

    private static final List<String> BOOL_METHOD_PREFIXES = List.of("is", "has", "can", "should");

    private static @Nullable String suggestNameFromVerbBoolean(final String methodName, final MethodInsnNode insn) {
        if (insn.desc == null || !insn.desc.endsWith("Z")) { // only handle methods that return booleans
            return null;
        }

        String prefix = null;
        for (final String possiblePrefix : BOOL_METHOD_PREFIXES) {
            if (!possiblePrefix.equals(methodName) && methodName.startsWith(possiblePrefix)) {
                prefix = possiblePrefix;
                break;
            }
        }
        if (prefix == null) {
            return null;
        }

        if (Character.isUpperCase(methodName.charAt(prefix.length()))) {
            return methodName;
        } else {
            return null;
        }
    }

    private static final List<String> SINGLE_WORD_BOOL_METHOD_NAMES = List.of("is", "has");

    private static @Nullable String suggestNameFromSingleWorldVerbBoolean(
            final @Nullable HypoContext context, final String methodName, final MethodInsnNode insn)
            throws IOException {
        if (insn.desc == null || !insn.desc.endsWith("Z")) {
            return null;
        }

        final Optional<String> prefix = SINGLE_WORD_BOOL_METHOD_NAMES.stream()
                .filter(Predicate.isEqual(methodName))
                .findFirst();
        if (prefix.isEmpty()) {
            return null;
        }

        final MethodDescriptor descriptor = MethodDescriptor.of(insn.desc);
        final List<FieldType> paramTypes = descriptor.getParamTypes();
        if (paramTypes.size() != 1) {
            return null;
        }
        final String paramTypeDesc = paramTypes.get(0).toString();

        final AbstractInsnNode prev = insn.getPrevious();
        if (prev instanceof final FieldInsnNode fieldInsnNode
                && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                && fieldInsnNode.name != null
                && areStringAlphasUppercase(fieldInsnNode.name)) {
            return prefix.get() + convertStaticFieldNameToLocalVarName(fieldInsnNode);
        } else {
            if ("Lnet/minecraft/tags/TagKey;".equals(paramTypeDesc)) { // isTag is better than isTagKey
                return "isTag";
            }
            final String typeName = LvtTypeSuggester.suggestNameFromType(context, toJvmType(getType(paramTypeDesc)));
            return prefix.get() + Character.toUpperCase(typeName.charAt(0)) + typeName.substring(1);
        }
    }

    private static String convertStaticFieldNameToLocalVarName(final FieldInsnNode fieldInsnNode) {
        final StringBuilder output = new StringBuilder();
        for (final String s : fieldInsnNode.name.split("_")) {
            output.append(s.charAt(0)).append(s.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return output.toString();
    }

    private static boolean areStringAlphasUppercase(final String input) {
        for (int i = 0; i < input.length(); i++) {
            final char ch = input.charAt(i);
            if (Character.isAlphabetic(ch)) {
                if (!Character.isUpperCase(ch)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @Nullable String suggestNameFromAs(final String methodName) {
        if (!methodName.startsWith("as") || methodName.equals("as")) {
            return null;
        }

        final String baseName = methodName.substring(2);

        if (Character.isUpperCase(baseName.charAt(0))) {
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        } else {
            // if the name doesn't follow the typical `asName` scheme we can't be confident it's
            // really a "getter" method, so don't use it for a name
            return null;
        }
    }

    private static @Nullable String suggestNameFromNew(final String methodName, MethodInsnNode insn) {
        if (!methodName.startsWith("new") || methodName.equals("new")) {
            return null;
        }

        final @Nullable String result =
                switch (insn.owner) {
                    case "com/google/common/collect/Lists" -> "list";
                    case "com/google/common/collect/Maps" -> "map";
                    case "com/google/common/collect/Sets" -> "set";
                    default -> null;
                };
        if (result != null) {
            return result;
        }

        final String baseName = methodName.substring(3);

        if (Character.isUpperCase(baseName.charAt(0))) {
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        } else {
            // if the name doesn't follow the typical `newName` scheme we can't be confident it's
            // really a "getter" method, so don't use it for a name
            return null;
        }
    }

    private static @Nullable String suggestNameFromRead(final String methodName) {
        if (!methodName.startsWith("read") || methodName.equals("read")) {
            return null;
        }

        final String baseName = methodName.substring(4);

        if (Character.isUpperCase(baseName.charAt(0))) {
            return Character.toLowerCase(baseName.charAt(0)) + baseName.substring(1);
        } else {
            // if the name doesn't follow the typical `readName` scheme we can't be confident it's
            // really a "getter" method, so don't use it for a name
            return null;
        }
    }

    private static @Nullable String suggestNameFromLine(final String methodName) {
        if (methodName.equals("readLine")) {
            return "line";
        }
        return null;
    }

    private static final Type stringType = getType("Ljava/lang/String;");

    private static @Nullable String suggestNameFromStrings(final String methodName, final MethodInsnNode insn) {
        if (methodName.startsWith("split")) {
            if (insn.owner.equals("java/lang/String") || insn.owner.equals("com/google/common/base/Splitter")) {
                return "parts";
            }
        }

        if (methodName.equals("repeat") && Type.getReturnType(insn.desc).equals(stringType)) {
            return "repeated";
        }
        if (methodName.equals("indexOf") || methodName.equals("lastIndexOf")) {
            return "index";
        }
        if (methodName.equals("substring")) {
            return "sub";
        }
        if (methodName.equals("codePointAt")) {
            return "code";
        }
        if (methodName.equals("trim")) {
            return "trimmed";
        }
        if (methodName.startsWith("strip")) {
            return "stripped";
        }
        if (methodName.equals("formatted")) {
            return "formatted";
        }

        return null;
    }
}
