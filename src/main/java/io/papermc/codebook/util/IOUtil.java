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

package io.papermc.codebook.util;

import io.papermc.codebook.exceptions.UnexpectedException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class IOUtil {

    private IOUtil() {}

    public static Path createTempDir(final String prefix) {
        try {
            return Files.createTempDirectory(Path.of("."), prefix);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create temporary directory", e);
        }
    }

    public static Path absolutePath(final Path path) {
        return path.toAbsolutePath().normalize();
    }

    public static String absolutePathString(final Path path) {
        return absolutePath(path).toString();
    }

    public static void deleteIfExists(final Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to delete file " + file, e);
        }
    }

    public static void createDirectories(final Path file) {
        try {
            Files.createDirectories(file);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create directory " + file.getParent(), e);
        }
    }

    public static void createParentDirectories(final Path file) {
        createDirectories(file.getParent());
    }

    public static void copy(final Path in, final Path out) {
        try {
            Files.copy(in, out);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to copy file from " + in + " to " + out, e);
        }
    }

    public static void move(final Path source, final Path dest) {
        try {
            Files.move(source, dest);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to move file from " + source + " to " + dest, e);
        }
    }

    public static void deleteRecursively(final Path dir) {
        ArrayDeque<Path> files = new ArrayDeque<>();
        // don't delete files inside the stream - file handles may still be held
        try (final Stream<Path> stream = Files.walk(dir)) {
            files = stream.collect(Collectors.toCollection(ArrayDeque::new));
        } catch (final IOException ignored) {
        }

        files.descendingIterator().forEachRemaining(IOUtil::deleteIfExists);
    }
}
