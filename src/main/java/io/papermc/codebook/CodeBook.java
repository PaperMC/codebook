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

package io.papermc.codebook;

import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.config.CodeBookCoordsResource;
import io.papermc.codebook.config.CodeBookResource;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.pages.ExtractVanillaJarPage;
import io.papermc.codebook.pages.InspectJarPage;
import io.papermc.codebook.pages.MergeMappingsPage;
import io.papermc.codebook.pages.RemapJarPage;
import io.papermc.codebook.pages.UnpickPage;
import io.papermc.codebook.util.IOUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class CodeBook {

    private final CodeBookContext ctx;

    public CodeBook(final CodeBookContext ctx) {
        this.ctx = ctx;
    }

    public void exec() {
        final Path tempDir = IOUtil.createTempDir(".tmp_codebook");

        try {
            this.exec(tempDir);
        } finally {
            IOUtil.deleteRecursively(tempDir);
        }
    }

    private void exec(final Path tempDir) {
        this.deleteOutputFile();

        final @Nullable CodeBookResource mappings = this.ctx.input().resolveMappings(this.ctx, tempDir);
        if (mappings == null) {
            throw new IllegalStateException("No mappings file could be determined for the given configuration");
        }

        final Path inputJar = this.ctx.input().resolveInputFile(tempDir);
        final ExtractVanillaJarPage extractPage = new ExtractVanillaJarPage(inputJar, tempDir);
        final Path extractedJar = extractPage.extract();

        final Path mappingsFile = mappings.resolveResourceFile(tempDir);
        final @Nullable Path paramMappingsFile;
        if (this.ctx.paramMappings() != null) {
            paramMappingsFile = this.ctx.paramMappings().resolveResourceFile(tempDir);
        } else {
            paramMappingsFile = null;
        }

        final MergeMappingsPage merger = new MergeMappingsPage(mappingsFile, paramMappingsFile, tempDir);
        final MappingSet initialMerged = merger.merge();
        final MappingSet merged =
                new InspectJarPage(extractedJar, extractPage.getLibraries(), initialMerged, tempDir).inspect();

        final Path remapperJar = this.ctx.remapperJar().resolveResourceFile(tempDir);

        final Path remapped =
                new RemapJarPage(remapperJar, extractedJar, extractPage.getLibraries(), tempDir, merged).remap();

        final @Nullable Path inputConstantsJar;
        if (this.ctx.constantsJar() != null) {
            inputConstantsJar = this.ctx.constantsJar().resolveResourceFile(tempDir);
        } else {
            inputConstantsJar = null;
        }

        // we need to have param mappings to do any unpicking
        unpick:
        if (paramMappingsFile != null) {
            // The param file needs to be a jar, which contains the parameter mappings as well as unpick definitions.
            // We can handle just a plain mappings file for the remapping step, but not for unpicking.
            if (!paramMappingsFile.getFileName().toString().endsWith(".jar")) {
                break unpick;
            }

            // And we need to have a constants jar. If one is provided, we'll use it
            // Otherwise, if we know the Maven coordinates for the mappings, we can use it to find the
            // corresponding constants jar too
            final Path constantsJar;
            if (inputConstantsJar != null) {
                constantsJar = inputConstantsJar;
            } else if (this.ctx.paramMappings() instanceof final CodeBookCoordsResource coords) {
                constantsJar = this.downloadConstantsJar(coords, tempDir);
            } else {
                break unpick;
            }

            final @Nullable MappingSet originalParamMappings = merger.getOriginalParamMappings();
            if (originalParamMappings == null) {
                throw new UnexpectedException("Failed to find original parameter mappings");
            }

            final Path unpicked = new UnpickPage(
                            remapped,
                            extractPage.getLibraries(),
                            tempDir,
                            paramMappingsFile,
                            constantsJar,
                            originalParamMappings,
                            merged)
                    .unpick();

            IOUtil.move(unpicked, this.ctx.outputJar());
            return;
        }

        IOUtil.move(remapped, this.ctx.outputJar());
    }

    private void deleteOutputFile() {
        if (Files.isRegularFile(this.ctx.outputJar())) {
            if (!this.ctx.overwrite()) {
                throw new UserErrorException("Cannot write output file " + this.ctx.outputJar()
                        + " as it already exists, and --force was not specified.");
            }

            IOUtil.deleteIfExists(this.ctx.outputJar());
        }
    }

    private Path downloadConstantsJar(final CodeBookCoordsResource coords, final Path tempDir) {
        return new CodeBookCoordsResource(coords.coords(), "constants", null, this.ctx.mavenBaseUrl())
                .resolveResourceFile(tempDir);
    }
}
