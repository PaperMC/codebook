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
import io.papermc.codebook.util.JarRunner;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import net.fabricmc.lorenztiny.TinyMappingFormat;
import org.cadixdev.lorenz.MappingSet;

public final class RemapJarPage extends CodeBookPage {

    private final List<Path> tinyRemapper;

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;
    private final MappingSet mappings;

    @Inject
    public RemapJarPage(
            @RemapperJar final List<Path> tinyRemapper,
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @TempDir final Path tempDir,
            @Mappings final MappingSet mappings) {
        this.tinyRemapper = tinyRemapper;
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
        this.mappings = mappings;
    }

    @Override
    public void exec() {
        final Path remapped = this.tempDir.resolve("remapped.jar");

        final Path mappingsFile = this.tempDir.resolve("merged.tiny");
        try {
            TinyMappingFormat.TINY_2.write(this.mappings, mappingsFile, "official", "mojang+yarn");
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to write out merged yarn mappings", e);
        }

        JarRunner.of("tiny-remapper", this.tinyRemapper)
                .withArgs(
                        IOUtil.absolutePathString(this.inputJar),
                        IOUtil.absolutePathString(remapped),
                        IOUtil.absolutePathString(mappingsFile),
                        "official",
                        "mojang+yarn")
                .withArgs(
                        this.classpath.stream().map(IOUtil::absolutePathString).toList())
                .withArgs(
                        "--threads=1",
                        "--fixpackageaccess",
                        "--rebuildsourcefilenames",
                        "--renameinvalidlocals",
                        "--invalidlvnamepattern=\\$\\$\\d+")
                .run();

        this.bind(InputJar.KEY).to(remapped);
    }
}
