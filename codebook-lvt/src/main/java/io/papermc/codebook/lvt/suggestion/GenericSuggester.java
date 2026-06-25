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

import io.papermc.codebook.lvt.suggestion.context.AssignmentContext;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.SuggesterContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import org.checkerframework.checker.nullness.qual.Nullable;

public class GenericSuggester implements LvtSuggester {

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call,
            final MethodInsnContext insn,
            final ContainerContext container,
            final AssignmentContext assignment,
            final SuggesterContext suggester) {
        return switch (call.data().name()) {
            case "hashCode" -> "hashCode";
            case "size" -> "size";
            case "length" -> "len";
            case "freeze" -> "frozen";
            case "readLine" -> "line";
            default -> null;
        };
    }
}
