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

import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.types.ClassType;
import dev.denwav.hypo.model.data.types.JvmType;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.lvt.suggestion.InjectedLvtSuggester;
import io.papermc.codebook.lvt.suggestion.context.LvtContext.Method;
import java.io.IOException;
import org.checkerframework.checker.nullness.qual.EnsuresNonNull;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;

// primitive methods in RandomSource
public class RandomSourceSuggester extends InjectedLvtSuggester {

    static final JvmType RANDOM_SOURCE_TYPE = new ClassType("net/minecraft/util/RandomSource");

    @MonotonicNonNull
    private ClassData randomSourceClass = null;

    @Override
    public @Nullable String suggestFromMethod(final Method method) throws IOException {
        final String methodName = method.data().name();
        ClassData ownerClass = method.owner();
        if (ownerClass.doesExtendOrImplement(this.randomSourceClass())) {
            ownerClass = this.randomSourceClass();
        }

        if (!ownerClass.equals(this.randomSourceClass())) {
            return null;
        }

        if (!methodName.startsWith("next") || "next".equals(methodName)) {
            return null;
        }

        return createNextRandomName(method.data());
    }

    @EnsuresNonNull("randomSourceClass")
    private ClassData randomSourceClass() throws IOException {
        if (this.randomSourceClass == null) {
            final @Nullable ClassData random =
                    this.hypoContext.getContextProvider().findClass(RANDOM_SOURCE_TYPE);
            if (random == null) {
                throw new UnexpectedException("Cannot find " + RANDOM_SOURCE_TYPE + " on the classpath.");
            }
            this.randomSourceClass = random;
        }
        return this.randomSourceClass;
    }
}
