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

import dev.denwav.hypo.asm.HypoAsmUtil;
import dev.denwav.hypo.model.data.types.JvmType;
import java.util.List;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;

public final class LvtUtil {

    private LvtUtil() {}

    public static JvmType toJvmType(final String desc) {
        return HypoAsmUtil.toJvmType(Type.getType(desc));
    }

    public static String capitalize(final String name, final int index) {
        if (name.isEmpty()) {
            return name;
        }
        return Character.toUpperCase(name.charAt(index)) + name.substring(index + 1);
    }

    public static String decapitalize(final String name) {
        boolean capturingGroup = false;
        final StringBuilder result = new StringBuilder();

        for (int i = 0; i < name.length(); i++) {
            final char character = name.charAt(i);
            if (Character.isUpperCase(character)) {
                if (capturingGroup) {
                    if (i < name.length() - 1 && Character.isLowerCase(name.charAt(i + 1))) {
                        // Next char is lowercase, so this is the start of a new word
                        result.append(character);
                    } else {
                        // Convert the leading capital to lowercase and append to the result
                        result.append(Character.toLowerCase(character));
                    }
                } else {
                    // let's start a group, making sure to lowercase if it's the first char of the name
                    capturingGroup = true;
                    result.append(i == 0 ? Character.toLowerCase(character) : character);
                }
            } else {
                capturingGroup = false;
                result.append(character);
            }
        }

        return result.toString();
    }

    public static Predicate<String> equalsAny(final String... strings) {
        return s -> {
            for (final String string : strings) {
                if (string.equals(s)) {
                    return true;
                }
            }
            return false;
        };
    }

    public static String findNextWord(final int start, final String str) {
        final StringBuilder nextWord = new StringBuilder();
        for (int i = start; i < str.length(); i++) {
            final char ch = str.charAt(i);
            if (nextWord.isEmpty()) {
                nextWord.append(ch);
            } else if (!Character.isUpperCase(ch)) {
                nextWord.append(ch);
            } else {
                break;
            }
        }
        return nextWord.toString();
    }

    public static @Nullable String tryMatchPrefix(final String methodName, final List<String> possiblePrefixes) {
        // skip any exact match
        if (possiblePrefixes.contains(methodName)) {
            return null;
        }
        @Nullable String prefix = null;
        for (final String possiblePrefix : possiblePrefixes) {
            if (hasPrefix(methodName, possiblePrefix)) {
                prefix = possiblePrefix;
                break;
            }
        }
        return prefix;
    }

    public static boolean isStringAllUppercase(final String input) {
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

    public static boolean hasPrefix(final String text, final String prefix) {
        return text.length() > prefix.length() && text.startsWith(prefix);
    }

    public static String parseSimpleTypeName(final String simpleName) {
        // Parse all capitalized types into lowercase
        // UUID -> uuid
        // AABB -> aabb
        if (LvtUtil.isStringAllUppercase(simpleName)) {
            return simpleName.toLowerCase();
        }

        // Decapitalize
        // HelloWorld -> helloWorld
        // abstractUUIDFix -> abstractUuidFix
        // myCoolAABBClass -> myCoolAabbClass
        return LvtUtil.decapitalize(simpleName);
    }

    @Nullable
    public static String parseSimpleTypeNameFromMethod(final String methodName, int prefix) {
        if (!Character.isUpperCase(methodName.charAt(prefix))) {
            // If the char isn't uppercase, that means it isn't following the typical `lowerCamelCase`
            // Java method naming scheme how we expect, so we can't be sure it means what we think it
            // means in this instance
            return null;
        } else {
            return LvtUtil.parseSimpleTypeName(methodName.substring(prefix));
        }
    }

    public static @Nullable AbstractInsnNode prevInsnIgnoringConvertCast(final AbstractInsnNode insn) {
        @Nullable AbstractInsnNode prev = insn.getPrevious();
        while (prev != null
                && (prev.getOpcode() == Opcodes.CHECKCAST
                        || (prev.getOpcode() >= Opcodes.I2L && prev.getOpcode() <= Opcodes.I2S))) {
            prev = prev.getPrevious();
        }
        return prev;
    }
}
