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
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class StripSignaturesPage extends CodeBookPage {

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
        try (final ZipFile inputZip = new ZipFile(this.inputJar.toFile())) {
            final @Nullable Manifest strippedManifest = stripManifestDigestEntries(inputZip);
            if (strippedManifest == null && !containsSignatureFiles(inputZip)) {
                return;
            }

            final Path strippedJar = this.tempDir.resolve("unsigned-" + this.inputJar.getFileName());
            IOUtil.deleteIfExists(strippedJar);

            try (final ZipOutputStream outputZip =
                    new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(strippedJar)))) {
                outputZip.setComment(inputZip.getComment());
                copyContents(inputZip, outputZip, strippedManifest);
            }

            this.bind(InputJar.KEY).to(strippedJar);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to strip jar signatures", e);
        }
    }

    private static boolean containsSignatureFiles(final ZipFile inputZip) {
        return inputZip.stream().anyMatch(entry -> isSignatureFile(entry.getName()));
    }

    private static void copyContents(
            final ZipFile inputZip, final ZipOutputStream outputZip, final @Nullable Manifest strippedManifest)
            throws IOException {
        final var entries = inputZip.entries();
        while (entries.hasMoreElements()) {
            final ZipEntry entry = entries.nextElement();
            if (isSignatureFile(entry.getName())) {
                continue;
            }

            final boolean rewriteManifest = strippedManifest != null && isManifestFile(entry.getName());
            final ZipEntry outputEntry = new ZipEntry(entry);
            if (outputEntry.getMethod() == ZipEntry.DEFLATED || rewriteManifest) {
                // Recalculate compression metadata, including for a previously STORED manifest.
                outputEntry.setMethod(ZipEntry.DEFLATED);
                outputEntry.setCompressedSize(-1);
            }
            outputZip.putNextEntry(outputEntry);
            if (rewriteManifest) {
                strippedManifest.write(outputZip);
            } else {
                try (final InputStream input = inputZip.getInputStream(entry)) {
                    input.transferTo(outputZip);
                }
            }
            outputZip.closeEntry();
        }
    }

    private static @Nullable Manifest stripManifestDigestEntries(final ZipFile inputZip) throws IOException {
        final ZipEntry manifestEntry = inputZip.getEntry("META-INF/MANIFEST.MF");
        if (manifestEntry == null) {
            return null;
        }

        final Manifest manifest;
        try (final InputStream input = inputZip.getInputStream(manifestEntry)) {
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
}
