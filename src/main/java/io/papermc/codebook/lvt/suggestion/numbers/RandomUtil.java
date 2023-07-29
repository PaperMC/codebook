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

package io.papermc.codebook.lvt.suggestion.numbers;

import static io.papermc.codebook.lvt.LvtUtil.equalsAny;
import static io.papermc.codebook.lvt.LvtUtil.findNextWord;

import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import java.util.function.Predicate;
import org.checkerframework.checker.nullness.qual.Nullable;

final class RandomUtil {

    private RandomUtil() {}

    static @Nullable String createNextRandomName(final MethodData method) {
        final @Nullable Predicate<String> expectedNextWord = expectedNextWordForRandomGen(method);
        if (expectedNextWord == null) {
            return null;
        }

        final String nextWord = findNextWord("next".length(), method.name());
        if (expectedNextWord.test(nextWord)) {
            return "random" + nextWord;
        }
        return null;
    }

    static @Nullable Predicate<String> expectedNextWordForRandomGen(final MethodData method) {
        if (!(method.returnType() instanceof final PrimitiveType primitiveType)) {
            return null;
        }

        return switch (primitiveType) {
            case CHAR -> equalsAny("Char", "Character");
            case BYTE -> equalsAny("Byte");
            case SHORT -> equalsAny("Short");
            case INT -> equalsAny("Int", "Integer");
            case LONG -> equalsAny("Long");
            case FLOAT -> equalsAny("Float");
            case DOUBLE -> equalsAny("Double");
            case BOOLEAN -> equalsAny("Bool", "Boolean");
            default -> null;
        };
    }
}
