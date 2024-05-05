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

import io.papermc.codebook.util.IOUtil;
import io.papermc.codebook.util.JarRunner;
import jakarta.inject.Inject;
import java.nio.file.Path;
import java.util.List;

public final class RemapJarPage extends CodeBookPage {

    private final List<Path> remapper;

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;
    private final Path mojangMappings;

    @Inject
    public RemapJarPage(
            @RemapperJar final List<Path> remapper,
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @TempDir final Path tempDir,
            @MojangMappings final Path mojangMappings) {
        this.remapper = remapper;
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
        this.mojangMappings = mojangMappings;
    }

    @Override
    public void exec() {
        final Path remapped = this.tempDir.resolve("remapped.jar");

        JarRunner.of("AutoRenamingTool", this.remapper)
                .withArgs(
                        "--input=" + IOUtil.absolutePathString(this.inputJar),
                        "--output=" + IOUtil.absolutePathString(remapped),
                        "--map=" + IOUtil.absolutePathString(this.mojangMappings),
                        "--reverse")
                .withArgs(this.classpath.stream()
                        .map(IOUtil::absolutePathString)
                        .map(s -> "--lib=" + s)
                        .toList())
                .withArgs("--src-fix", "--strip-sigs", "--disable-abstract-param")
                .run();

        this.bind(InputJar.KEY).to(remapped);
    }
}
