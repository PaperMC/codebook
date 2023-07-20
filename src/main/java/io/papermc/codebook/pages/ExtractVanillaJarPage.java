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

import at.favre.lib.bytes.Bytes;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.util.IOUtil;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ExtractVanillaJarPage extends CodeBookPage {

    private final Path inputJar;
    private final @Nullable List<Path> classpathJars;
    private final Path tempDir;

    @Inject
    public ExtractVanillaJarPage(
            @InputJar final Path inputJar,
            @ClasspathJars final @Nullable List<Path> classpathJars,
            @TempDir final Path tempDir) {
        this.inputJar = inputJar;
        this.classpathJars = classpathJars;
        this.tempDir = tempDir;
    }

    @Override
    public void exec() {
        if (this.classpathJars != null) {
            // nothing to do
            return;
        }

        try (final FileSystem inFs = FileSystems.newFileSystem(this.inputJar)) {
            final var rootDir = inFs.getPath("/");
            final var versionsDir = rootDir.resolve("META-INF/versions");
            final var librariesDir = rootDir.resolve("META-INF/libraries");

            final var versionsFile = rootDir.resolve("META-INF/versions.list");
            final var version = Version.parseFile(versionsFile);

            final var librariesFile = rootDir.resolve("META-INF/libraries.list");
            final var libraries = Library.parseFile(librariesFile);

            final var internalPath = Path.of(version.filePath);

            final var serverJar = this.tempDir.resolve(internalPath.getFileName());
            IOUtil.copy(versionsDir.resolve(version.filePath), serverJar);

            final var actualHash = Bytes.from(serverJar.toFile()).hashSha256().encodeHex();
            if (!version.sha256.equalsIgnoreCase(actualHash)) {
                throw new UnexpectedException("Failed to copy " + internalPath.getFileName()
                        + " from vanilla jar successfully (hash does not match)");
            }

            final var libs = new ArrayList<Path>();
            this.bind(ClasspathJars.KEY).to(libs);

            final var outLibsDir = this.tempDir.resolve("libraries");
            IOUtil.createDirectories(outLibsDir);
            for (final var library : libraries) {
                final var libInternalPath = Path.of(library.filePath);
                final var libFile = outLibsDir.resolve(libInternalPath.getFileName());
                IOUtil.copy(librariesDir.resolve(library.filePath), libFile);

                libs.add(libFile);

                final var libActualHash =
                        Bytes.from(libFile.toFile()).hashSha256().encodeHex();
                if (!library.sha256.equalsIgnoreCase(libActualHash)) {
                    throw new UnexpectedException("Failed to copy " + libInternalPath.getFileName()
                            + " from vanilla jar successfully (hash does not match)");
                }
            }

            this.bind(InputJar.KEY).to(serverJar);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to filter jar", e);
        }
    }

    private record Version(String sha256, String version, String filePath) {
        private static Version parseFile(final Path file) {
            final List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (final IOException e) {
                throw new UnexpectedException("Failed to read versions.list file", e);
            }

            if (lines.isEmpty()) {
                throw new UnexpectedException("versions.list file is empty");
            }
            final String[] parts = lines.get(0).split("\t");
            if (parts.length != 3) {
                throw new UnexpectedException("versions.list file is invalid");
            }

            return new Version(parts[0], parts[1], parts[2]);
        }
    }

    private record Library(String sha256, String mavenCoords, String filePath) {
        private static List<Library> parseFile(final Path file) {
            final List<String> lines;
            try {
                lines = Files.readAllLines(file);
            } catch (final IOException e) {
                throw new UnexpectedException("Failed to read libraries.list file", e);
            }

            if (lines.isEmpty()) {
                throw new UnexpectedException("libraries.list file is empty");
            }

            return lines.stream()
                    .map(l -> {
                        final String[] parts = l.split("\t");
                        if (parts.length != 3) {
                            throw new UnexpectedException("versions.list file is invalid");
                        }
                        return new Library(parts[0], parts[1], parts[2]);
                    })
                    .toList();
        }
    }
}
