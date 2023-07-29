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
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class UnpickPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;
    private final @Nullable Path unpickDefinitions;
    private final @Nullable Path constantsJar;

    @Inject
    public UnpickPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @TempDir final Path tempDir,
            @UnpickDefinitions final @Nullable Path unpickDefinitions,
            @ConstantsJar final @Nullable Path constantsJar) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
        this.unpickDefinitions = unpickDefinitions;
        this.constantsJar = constantsJar;
    }

    public void exec() {
        if (this.unpickDefinitions == null || this.constantsJar == null) {
            return;
        }

        final Path outputJar = this.tempDir.resolve("unpicked.jar");

        final var args = new ArrayList<String>();
        args.addAll(List.of(
                IOUtil.absolutePathString(this.inputJar),
                IOUtil.absolutePathString(outputJar),
                IOUtil.absolutePathString(this.unpickDefinitions),
                IOUtil.absolutePathString(this.constantsJar)));
        args.addAll(this.classpath.stream().map(IOUtil::absolutePathString).toList());

        try {
            daomephsta.unpick.cli.Main.main(args.toArray(new String[0]));
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to run unpick", e);
        }

        this.bind(InputJar.KEY).to(outputJar);
    }
}
