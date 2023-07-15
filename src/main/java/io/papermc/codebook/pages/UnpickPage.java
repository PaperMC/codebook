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
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.util.IOUtil;
import io.papermc.codebook.util.unpick.UnpickFilter;
import io.papermc.codebook.util.unpick.UnpickV2LorenzRemapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.cadixdev.lorenz.MappingSet;

public final class UnpickPage {

    private static final List<String> UNPICK_EXCLUDES = List.of("net/minecraft/client/");

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;
    private final Path unpickDefinitionsJar;
    private final Path constantsJar;
    private final MappingSet originalMappings;
    private final MappingSet mergedMappings;

    public UnpickPage(
            final Path inputJar,
            final List<Path> classpath,
            final Path tempDir,
            final Path unpickDefinitionsJar,
            final Path constantsJar,
            final MappingSet originalMappings,
            final MappingSet mergedMappings) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
        this.unpickDefinitionsJar = unpickDefinitionsJar;
        this.constantsJar = constantsJar;
        this.originalMappings = originalMappings;
        this.mergedMappings = mergedMappings;
    }

    public Path unpick() {
        final Path unpickDefinitions = this.remapUnpickDefinitions();

        final Path outputJar = this.tempDir.resolve("unpicked.jar");

        final var args = new ArrayList<String>();
        args.addAll(List.of(
                IOUtil.absolutePathString(this.inputJar),
                IOUtil.absolutePathString(outputJar),
                IOUtil.absolutePathString(unpickDefinitions),
                IOUtil.absolutePathString(this.constantsJar)));
        args.addAll(this.classpath.stream().map(IOUtil::absolutePathString).toList());

        try {
            daomephsta.unpick.cli.Main.main(args.toArray(new String[0]));
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to run unpick", e);
        }

        return outputJar;
    }

    private Path remapUnpickDefinitions() {
        final Path unpickDefinitions = this.tempDir.resolve("definitions.unpick");
        try (final FileSystem fs = FileSystems.newFileSystem(this.unpickDefinitionsJar)) {
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
        return this.originalMappings.reverse().merge(this.mergedMappings);
    }
}
