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

import static io.papermc.codebook.lvt.LvtUtil.hasPrefix;
import static io.papermc.codebook.lvt.LvtUtil.parseSimpleTypeNameFromMethod;

import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;

public class NewPrefixSuggester implements LvtSuggester {

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container)
            throws IOException {
        final String methodName = call.data().name();
        if (!hasPrefix(methodName, "new")) {
            return null;
        }

        final @Nullable String result =
                switch (insn.owner().name()) {
                    case "com/google/common/collect/Lists" -> "list";
                    case "com/google/common/collect/Maps" -> "map";
                    case "com/google/common/collect/Sets" -> "set";
                    default -> null;
                };
        if (result != null) {
            return result;
        }

        return parseSimpleTypeNameFromMethod(methodName, 3);
    }
}
