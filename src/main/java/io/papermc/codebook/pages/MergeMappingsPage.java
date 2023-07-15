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

package io.papermc.codebook.pages;

import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.util.IOUtil;
import io.papermc.codebook.util.ParamsMergeHandler;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingFormats;
import org.cadixdev.lorenz.merge.FieldMergeStrategy;
import org.cadixdev.lorenz.merge.MappingSetMerger;
import org.cadixdev.lorenz.merge.MergeConfig;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class MergeMappingsPage {

    private final Path mojmapMappings;
    private final @Nullable Path paramMappings;
    private final Path tempDir;

    private @Nullable MappingSet originalParamMappings = null;

    public MergeMappingsPage(final Path mojmapMappings, final @Nullable Path paramMappings, final Path tempDir) {
        this.mojmapMappings = mojmapMappings;
        this.paramMappings = paramMappings;
        this.tempDir = tempDir;
    }

    public MappingSet merge() {
        final MappingSet mojmap = this.getMojmapMappings();
        final @Nullable MappingSet params = this.getParamMappings();
        this.originalParamMappings = params;

        if (params == null) {
            return mojmap;
        }

        final MergeConfig mergeConfig = MergeConfig.builder()
                .withFieldMergeStrategy(FieldMergeStrategy.STRICT)
                .withMergeHandler(new ParamsMergeHandler())
                .build();
        return MappingSetMerger.create(mojmap, params, mergeConfig).merge();
    }

    private MappingSet getMojmapMappings() {
        try {
            return MappingFormats.byId("proguard").read(this.mojmapMappings).reverse();
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to read mojmap mappings file", e);
        }
    }

    private @Nullable MappingSet getParamMappings() {
        if (this.paramMappings == null) {
            return null;
        }

        final Path actualMappingsFile;
        if (this.paramMappings.getFileName().toString().endsWith(".jar")) {
            try (final var fs = FileSystems.newFileSystem(this.paramMappings)) {
                final var path = fs.getPath("mappings", "mappings.tiny");
                actualMappingsFile = this.tempDir.resolve("original_params.tiny");
                IOUtil.copy(path, actualMappingsFile);
            } catch (final IOException e) {
                throw new UnexpectedException("Failed to extract parameter mappings", e);
            }
        } else {
            actualMappingsFile = this.paramMappings;
        }

        try {
            return TinyMappingFormat.TINY_2.read(actualMappingsFile, "official", "named");
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to read parameter mappings file", e);
        }
    }

    public @Nullable MappingSet getOriginalParamMappings() {
        return this.originalParamMappings;
    }
}
