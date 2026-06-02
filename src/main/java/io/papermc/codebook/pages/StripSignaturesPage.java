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
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class StripSignaturesPage extends CodeBookPage {

    private static final Map<String, String> CREATE_FS_ARGS = Map.of("create", "true");
    private static final String DIGEST_SUFFIX = "-digest";
    private static final int DIGEST_SUFFIX_LENGTH = DIGEST_SUFFIX.length();

    private final Path inputJar;
    private final Path tempDir;

    @Inject
    public StripSignaturesPage(@InputJar final Path inputJar, @TempDir final Path tempDir) {
        this.inputJar = inputJar;
        this.tempDir = tempDir;
    }

    @Override
    public void exec() {
        try (final FileSystem inputFs = FileSystems.newFileSystem(this.inputJar)) {
            final Path inputRoot = inputFs.getPath("/");
            final @Nullable Manifest strippedManifest = stripManifestDigestEntries(inputRoot);
            if (strippedManifest == null && !containsSignatureFiles(inputRoot)) {
                return;
            }

            final Path strippedJar = this.tempDir.resolve("unsigned-" + this.inputJar.getFileName());
            IOUtil.deleteIfExists(strippedJar);

            try (final FileSystem outputFs = FileSystems.newFileSystem(strippedJar, CREATE_FS_ARGS)) {
                copyContents(inputRoot, outputFs.getPath("/"), strippedManifest);
            }

            this.bind(InputJar.KEY).to(strippedJar);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to strip jar signatures", e);
        }
    }

    private static boolean containsSignatureFiles(final Path inputRoot) throws IOException {
        try (final var paths = Files.walk(inputRoot)) {
            for (final Path path : (Iterable<Path>) paths::iterator) {
                if (isSignatureFile(pathName(inputRoot.relativize(path)))) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void copyContents(
            final Path inputRoot, final Path outputRoot, final @Nullable Manifest strippedManifest) throws IOException {
        try (final var paths = Files.walk(inputRoot)) {
            for (final Path sourcePath : (Iterable<Path>) paths::iterator) {
                final Path relativePath = inputRoot.relativize(sourcePath);
                final String entryName = pathName(relativePath);
                if (entryName.isEmpty() || isSignatureFile(entryName)) {
                    continue;
                }

                final Path outputPath = outputRoot.resolve(relativePath.toString());
                if (Files.isDirectory(sourcePath)) {
                    Files.createDirectories(outputPath);
                } else if (strippedManifest != null && isManifestFile(entryName)) {
                    IOUtil.createParentDirectories(outputPath);
                    writeManifest(outputPath, strippedManifest);
                } else {
                    IOUtil.createParentDirectories(outputPath);
                    Files.copy(sourcePath, outputPath);
                }
            }
        }
    }

    private static @Nullable Manifest stripManifestDigestEntries(final Path inputRoot) throws IOException {
        final Path manifestPath = inputRoot.resolve("META-INF/MANIFEST.MF");
        if (Files.notExists(manifestPath)) {
            return null;
        }

        final Manifest manifest;
        try (final InputStream input = Files.newInputStream(manifestPath)) {
            manifest = new Manifest(input);
        }

        boolean madeChanges = false;
        for (final Iterator<Attributes> it = manifest.getEntries().values().iterator(); it.hasNext(); ) {
            final Attributes entryAttributes = it.next();
            if (entryAttributes.keySet().removeIf(StripSignaturesPage::isDigestAttribute)) {
                madeChanges = true;
                if (entryAttributes.isEmpty()) {
                    it.remove();
                }
            }
        }

        return madeChanges ? manifest : null;
    }

    private static boolean isDigestAttribute(final Object key) {
        final String attributeName = key.toString();
        if (attributeName.length() <= DIGEST_SUFFIX_LENGTH) {
            return false;
        }
        return attributeName.regionMatches(
                true, attributeName.length() - DIGEST_SUFFIX_LENGTH, DIGEST_SUFFIX, 0, DIGEST_SUFFIX_LENGTH);
    }

    private static boolean isSignatureFile(final String entryName) {
        return entryName.startsWith("META-INF/")
                && (entryName.endsWith(".RSA")
                        || entryName.endsWith(".SF")
                        || entryName.endsWith(".DSA")
                        || entryName.endsWith(".EC"));
    }

    private static boolean isManifestFile(final String entryName) {
        return entryName.equals("META-INF/MANIFEST.MF");
    }

    private static String pathName(final Path relativePath) {
        return relativePath.toString().replace('\\', '/');
    }

    private static void writeManifest(final Path outputPath, final Manifest manifest) throws IOException {
        try (final OutputStream output = Files.newOutputStream(outputPath)) {
            manifest.write(output);
        }
    }
}
