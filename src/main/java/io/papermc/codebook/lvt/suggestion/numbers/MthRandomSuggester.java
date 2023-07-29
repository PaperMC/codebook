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

import static io.papermc.codebook.lvt.suggestion.numbers.RandomUtil.createNextRandomName;

import dev.denwav.hypo.model.data.types.JvmType;
import io.papermc.codebook.lvt.suggestion.InjectedLvtSuggester;
import io.papermc.codebook.lvt.suggestion.context.LvtContext.Method;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

// primitive random-related methods in Mth
public class MthRandomSuggester extends InjectedLvtSuggester {

    static final String MTH_NAME = "net/minecraft/util/Mth";

    @Override
    public @Nullable String suggestFromMethod(final Method ctx) {
        final String methodName = ctx.data().name();
        if (!ctx.owner().name().equals(MTH_NAME)) {
            return null;
        }

        if (!methodName.startsWith("next") || "next".equals(methodName)) {
            return null;
        }

        final List<JvmType> params = ctx.data().params();
        if (params.isEmpty()
                || !params.get(0).asInternalName().equals(RandomSourceSuggester.RANDOM_SOURCE_TYPE.asInternalName())) {
            return null;
        }

        return createNextRandomName(ctx.data());
    }
}
