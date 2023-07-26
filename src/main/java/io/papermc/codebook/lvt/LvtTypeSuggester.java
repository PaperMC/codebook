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

import com.google.common.base.Splitter;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.types.ArrayType;
import dev.denwav.hypo.model.data.types.ClassType;
import dev.denwav.hypo.model.data.types.JvmType;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import java.io.IOException;
import java.util.List;
import org.jetbrains.annotations.Nullable;

public final class LvtTypeSuggester {

    private LvtTypeSuggester() {}

    public static String suggestNameFromType(final @Nullable HypoContext context, final JvmType type) throws IOException {
        if (type instanceof PrimitiveType) {
            return switch ((PrimitiveType) type) {
                case CHAR -> "c";
                case BYTE -> "b";
                case SHORT -> "s";
                case INT -> "i";
                case LONG -> "l";
                case FLOAT -> "f";
                case DOUBLE -> "d";
                case BOOLEAN -> "flag";
                case VOID -> throw new IllegalStateException("Illegal local variable type: " + type);
            };
        } else if (type instanceof ClassType) {
            return suggestNameFromClassType(context, (ClassType) type);
        } else if (type instanceof ArrayType) {
            final JvmType baseType = ((ArrayType) type).baseType();
            if (baseType instanceof PrimitiveType) {
                return switch (((PrimitiveType) baseType)) {
                    case CHAR -> "chars";
                    case BYTE -> "bytes";
                    case SHORT -> "shorts";
                    case INT -> "ints";
                    case LONG -> "longs";
                    case FLOAT -> "floats";
                    case DOUBLE -> "doubles";
                    case BOOLEAN -> "flags";
                    case VOID -> throw new IllegalStateException("Illegal local variable type: " + type);
                };
            } else {
                return suggestNameFromType(context, baseType) + "s";
            }
        } else {
            throw new IllegalStateException("Unknown type: " + type);
        }
    }

    private static String suggestNameFromClassType(final @Nullable HypoContext context, final ClassType type) throws IOException {
        final String name = type.asInternalName();
        if (name.equals("Ljava/lang/String;")) {
            return "string";
        }

        if (name.equals("Ljava/lang/Class;")) {
            return "clazz";
        }

        // TODO Try to determine name from signature, rather than just descriptor
        if (context != null) {
            final @Nullable ClassData typeClass = context.getContextProvider().findClass(type);
            if (typeClass != null) {
                @Nullable final ClassData listClass = context.getContextProvider().findClass("java/util/List");
                @Nullable final ClassData setClass = context.getContextProvider().findClass("java/util/Set");
                @Nullable final ClassData mapClass = context.getContextProvider().findClass("java/util/Map");

                if (listClass != null && typeClass.doesImplement(listClass)) {
                    return "list";
                } else if (setClass != null && typeClass.doesImplement(setClass)) {
                    return "set";
                } else if (mapClass != null && typeClass.doesImplement(mapClass)) {
                    return "map";
                }
            }
        }

        final String baseName = name.substring(1, name.length() - 1);
        final String simpleName = getSimpleName(baseName);

        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    private static final Splitter dollarSplitter = Splitter.on('$');

    private static String getSimpleName(final String name) {
        final int index = name.lastIndexOf('/');
        final String className;
        if (index != -1) {
            className = name.substring(index + 1);
        } else {
            className = name;
        }

        if (className.isBlank()) {
            return "var";
        }

        final int dollarIndex = className.lastIndexOf('$');
        if (dollarIndex == -1) {
            return className;
        }

        final List<String> parts = dollarSplitter.splitToList(name);
        for (int i = parts.size() - 1; i >= 0; i--) {
            final String part = parts.get(i);

            final int goodIndex = firstGoodIndex(part);
            if (goodIndex != -1) {
                if (goodIndex == 0) {
                    return part;
                } else {
                    return part.substring(goodIndex);
                }
            }
        }

        // found nothing good to work with
        return "var";
    }

    private static int firstGoodIndex(final String part) {
        final int len = part.length();
        int firstGoodIndex;
        for (firstGoodIndex = 0; firstGoodIndex < len; firstGoodIndex++) {
            final char c = part.charAt(firstGoodIndex);
            if (Character.isJavaIdentifierStart(c)) {
                break;
            }
        }
        if ((len - firstGoodIndex) <= 1) {
            return -1;
        }
        return firstGoodIndex;
    }
}
