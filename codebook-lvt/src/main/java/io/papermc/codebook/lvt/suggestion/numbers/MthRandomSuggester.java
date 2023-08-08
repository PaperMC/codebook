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

import static io.papermc.codebook.lvt.LvtUtil.hasPrefix;
import static io.papermc.codebook.lvt.suggestion.numbers.RandomUtil.createNextRandomName;

import dev.denwav.hypo.model.data.types.JvmType;
import io.papermc.codebook.lvt.suggestion.LvtSuggester;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

// primitive random-related methods in Mth
public class MthRandomSuggester implements LvtSuggester {

    static final String MTH_NAME = "net/minecraft/util/Mth";

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container) {
        final String methodName = call.data().name();
        if (!insn.ownerEqualTo(MTH_NAME)) {
            return null;
        }

        if (!hasPrefix(methodName, "next")) {
            return null;
        }

        final List<JvmType> params = call.data().params();
        if (params.isEmpty() || !params.get(0).equals(RandomSourceSuggester.RANDOM_SOURCE_TYPE)) {
            return null;
        }

        return createNextRandomName(call.data());
    }
}
