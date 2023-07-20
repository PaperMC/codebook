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

import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Reader;
import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Writer;
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.config.CodeBookCoordsResource;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.util.IOUtil;
import io.papermc.codebook.util.unpick.UnpickFilter;
import io.papermc.codebook.util.unpick.UnpickV2LorenzRemapper;
import jakarta.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class UnpickPage extends CodeBookPage {

    private static final List<String> UNPICK_EXCLUDES = List.of("net/minecraft/client/");

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;
    private final CodeBookContext ctx;
    private final @Nullable Path paramMappingsFile;
    private final @Nullable MappingSet paramMappings;
    private final MappingSet mergedMappings;

    @Inject
    public UnpickPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @TempDir final Path tempDir,
            @Context final CodeBookContext ctx,
            @ParamMappings final @Nullable Path paramMappingsFile,
            @ParamMappings final @Nullable MappingSet paramMappings,
            @Mappings final MappingSet mergedMappings) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
        this.ctx = ctx;
        this.paramMappingsFile = paramMappingsFile;
        this.paramMappings = paramMappings;
        this.mergedMappings = mergedMappings;
    }

    public void exec() {
        final @Nullable Path inputConstantsJar;
        if (this.ctx.constantsJar() != null) {
            inputConstantsJar = this.ctx.constantsJar().resolveResourceFile(this.tempDir);
        } else {
            inputConstantsJar = null;
        }

        // we need to have param mappings to do any unpicking
        if (this.paramMappingsFile == null) {
            return;
        }

        // The param file needs to be a jar, which contains the parameter mappings as well as unpick definitions.
        // We can handle just a plain mappings file for the remapping step, but not for unpicking.
        if (!this.paramMappingsFile.getFileName().toString().endsWith(".jar")) {
            return;
        }

        // And we need to have a constants jar. If one is provided, we'll use it
        // Otherwise, if we know the Maven coordinates for the mappings, we can use it to find the
        // corresponding constants jar too
        final Path constantsJar;
        if (inputConstantsJar != null) {
            constantsJar = inputConstantsJar;
        } else if (this.ctx.paramMappings() instanceof final CodeBookCoordsResource coords) {
            constantsJar = this.downloadConstantsJar(coords, this.tempDir);
        } else {
            return;
        }

        final Path unpickDefinitions = this.remapUnpickDefinitions(this.paramMappingsFile);

        final Path outputJar = this.tempDir.resolve("unpicked.jar");

        final var args = new ArrayList<String>();
        args.addAll(List.of(
                IOUtil.absolutePathString(this.inputJar),
                IOUtil.absolutePathString(outputJar),
                IOUtil.absolutePathString(unpickDefinitions),
                IOUtil.absolutePathString(constantsJar)));
        args.addAll(this.classpath.stream().map(IOUtil::absolutePathString).toList());

        try {
            daomephsta.unpick.cli.Main.main(args.toArray(new String[0]));
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to run unpick", e);
        }

        this.bind(InputJar.KEY).to(outputJar);
    }

    private Path remapUnpickDefinitions(final Path unpickDefinitionsJar) {
        final Path unpickDefinitions = this.tempDir.resolve("definitions.unpick");
        try (final FileSystem fs = FileSystems.newFileSystem(unpickDefinitionsJar)) {
            final Path inputDefs = fs.getPath("extras", "definitions.unpick");
            IOUtil.copy(inputDefs, unpickDefinitions);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed ot read unpick definitions from mappings jar", e);
        }

        final MappingSet mappings = this.getNamedToMojmap();

        final UnpickV2Writer writer = new UnpickV2Writer();
        try (final InputStream input = Files.newInputStream(unpickDefinitions)) {

            final UnpickV2LorenzRemapper remapper = new UnpickV2LorenzRemapper(mappings, writer);
            final UnpickFilter filter = new UnpickFilter(UNPICK_EXCLUDES, remapper);
            final UnpickV2Reader reader = new UnpickV2Reader(input);

            reader.accept(filter);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to read unpick definitions file (already extracted)", e);
        }

        final Path remappedDefinitions = this.tempDir.resolve("remapped_definitions.unpick");
        try {
            Files.writeString(remappedDefinitions, writer.getOutput());
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to write remapped unpick definitions", e);
        }

        return remappedDefinitions;
    }

    private MappingSet getNamedToMojmap() {
        return Objects.requireNonNull(this.paramMappings, "paramMappings")
                .reverse()
                .merge(this.mergedMappings);
    }

    private Path downloadConstantsJar(final CodeBookCoordsResource coords, final Path tempDir) {
        final String baseUrl = Objects.requireNonNull(this.ctx.mavenBaseUrl(), "mavenBaseUrl not set");
        return new CodeBookCoordsResource(coords.coords(), "constants", null, baseUrl).resolveResourceFile(tempDir);
    }
}
