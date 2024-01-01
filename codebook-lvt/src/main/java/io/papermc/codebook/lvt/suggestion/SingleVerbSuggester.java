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

import static io.papermc.codebook.lvt.LvtUtil.parseSimpleTypeNameFromMethod;
import static io.papermc.codebook.lvt.LvtUtil.tryMatchPrefix;

import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/*
This matches against methods with a set prefix and trims that prefix off of the
returned local variable name
 */
public class SingleVerbSuggester implements LvtSuggester {

    private static final List<String> SINGLE_VERB_PREFIXES = List.of("getOrCreate", "get", "as", "read");

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container) {
        final String methodName = call.data().name();

        final @Nullable String prefix = tryMatchPrefix(methodName, SINGLE_VERB_PREFIXES);
        if (prefix == null) {
            return null;
        }

        return parseSimpleTypeNameFromMethod(methodName, prefix.length());
    }
}
