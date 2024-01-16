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

import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.types.ClassType;
import dev.denwav.hypo.model.data.types.JvmType;
import io.papermc.codebook.lvt.suggestion.LvtSuggester;
import io.papermc.codebook.lvt.suggestion.context.AssignmentContext;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.SuggesterContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import jakarta.inject.Inject;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;

// primitive methods in RandomSource
public class RandomSourceSuggester implements LvtSuggester {

    static final JvmType RANDOM_SOURCE_TYPE = new ClassType("net/minecraft/util/RandomSource");

    private final @Nullable ClassData randomSourceClass;

    @Inject
    RandomSourceSuggester(final HypoContext hypoContext) throws IOException {
        this.randomSourceClass = hypoContext.getContextProvider().findClass(RANDOM_SOURCE_TYPE);
        if (this.randomSourceClass == null) {
            System.err.println("Failed to find RandomSource class, disabling RandomSourceSuggester");
        }
    }

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call,
            final MethodInsnContext insn,
            final ContainerContext container,
            final AssignmentContext assignment,
            final SuggesterContext suggester) {
        if (this.randomSourceClass == null) {
            return null;
        }

        final String methodName = call.data().name();
        ClassData ownerClass = insn.owner();
        if (ownerClass.doesExtendOrImplement(this.randomSourceClass)) {
            ownerClass = this.randomSourceClass;
        }

        if (!ownerClass.equals(this.randomSourceClass)) {
            return null;
        }

        if (!methodName.startsWith("next") || "next".equals(methodName)) {
            return null;
        }

        return createNextRandomName(call.data());
    }
}
