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

package io.papermc.codebook.config;

import io.soabase.recordbuilder.core.RecordBuilder;
import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

@RecordBuilder
// RecordBuilder unfortunately does not understand TYPE_USE, so to make it generate code with property
// nullability we need to explicitly place annotations which support targets other than TYPE_USE
@RecordBuilder.Options(interpretNotNulls = true)
public record CodeBookContext(
        @Nullable @org.jetbrains.annotations.Nullable Path tempDir,
        @NotNull CodeBookRemapper remapperJar,
        @Nullable @org.jetbrains.annotations.Nullable CodeBookResource mappings,
        @Nullable @org.jetbrains.annotations.Nullable CodeBookResource paramMappings,
        @Nullable @org.jetbrains.annotations.Nullable CodeBookResource unpickDefinitions,
        @Nullable @org.jetbrains.annotations.Nullable CodeBookResource constantsJar,
        @NotNull Path outputJar,
        boolean overwrite,
        @NotNull CodeBookInput input,
        boolean logMissingLvtSuggestions) {

    public static CodeBookContextBuilder builder() {
        return CodeBookContextBuilder.builder();
    }
}
