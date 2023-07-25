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

import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.MethodInsnNode;

public final class LvtAssignmentSuggester {

    private LvtAssignmentSuggester() {}

    public static @Nullable String suggestNameFromAssignment(final String methodName, final MethodInsnNode insn) {
        @Nullable String suggested = suggestGeneric(methodName);
        if (suggested != null) {
            return suggested;
        }

        suggested = suggestNameFromGetter(methodName);
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

    private static final Type stringType = Type.getType("Ljava/lang/String;");

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
