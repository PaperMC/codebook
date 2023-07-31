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

import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MathSuggester implements LvtSuggester {

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container)
            throws IOException {
        final String methodName = call.data().name();

        if (insn.ownerEqualTo("java/lang/Math")) {
            return switch (methodName) {
                case "max" -> "max";
                case "min" -> "min";
                case "sqrt" -> "squareRoot";
                case "sin" -> "sin";
                case "cos" -> "cos";
                case "tan" -> "tan";
                case "asin" -> "asin";
                case "acos" -> "acos";
                case "atan" -> "atan";
                case "atan2" -> "atan2";
                case "sinh" -> "sinh";
                case "cosh" -> "cosh";
                case "tanh" -> "tanh";
                case "ceil" -> "ceil";
                case "floor" -> "floor";
                case "round" -> "rounded";
                case "abs" -> "abs";
                default -> null;
            };
        }

        if (insn.ownerEqualTo("net/minecraft/util/Mth")) {
            return switch (methodName) {
                case "abs" -> "abs";
                case "absMax" -> "max";
                case "sin" -> "sin";
                case "cos" -> "cos";
                case "sqrt" -> "squareRoot";
                case "invSqrt", "fastInvSqrt" -> "inverseSquareRoot";
                case "ceil" -> "ceil";
                case "floor" -> "floor";
                case "roundToward" -> "rounded";
                case "square" -> "squared";
                case "hsvToRgb" -> "rgb";
                case "binarySearch" -> "index";
                case "frac" -> "fraction";
                case "color" -> "color";
                case "equal" -> "isEqual";
                default -> null;
            };
        }

        return null;
    }
}
