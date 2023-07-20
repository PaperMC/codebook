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

package io.papermc.codebook.config;

import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.util.Downloader;
import io.soabase.recordbuilder.core.RecordBuilder;
import java.net.URI;
import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.jetbrains.annotations.NotNull;

@RecordBuilder
@RecordBuilder.Options(interpretNotNulls = true)
public record CodeBookCoordsResource(
        @NotNull String coords,
        @Nullable @org.jetbrains.annotations.Nullable String classifier,
        @Nullable @org.jetbrains.annotations.Nullable String extension,
        @NotNull String mavenBaseUrl)
        implements CodeBookResource {

    public static CodeBookCoordsResourceBuilder builder() {
        return CodeBookCoordsResourceBuilder.builder();
    }

    @Override
    public Path resolveResourceFile(final Path tempDir) {
        this.verifyMavenCoords(this.coords);
        final URI uri = Downloader.getDownloadUri(this.mavenBaseUrl, this.getFullCoords());

        final String[] parts = this.coords.split(":");
        final var sb = new StringBuilder();
        sb.append(parts[1]).append('-').append(parts[2]);
        if (this.classifier != null) {
            sb.append('-').append(this.classifier);
        }
        sb.append('.').append(this.getExtension());
        final Path outputFile = tempDir.resolve(sb.toString());
        Downloader.downloadFile(uri, outputFile);

        return outputFile;
    }

    private void verifyMavenCoords(final String text) {
        if (text.split(":").length == 4) {
            throw new UserErrorException("Do not provide a classifier when specifying Maven coordinates.");
        }
    }

    private String getExtension() {
        return this.extension == null ? "jar" : this.extension;
    }

    private String getFullCoords() {
        final StringBuilder sb = new StringBuilder(this.coords);
        if (this.classifier != null) {
            sb.append(':').append(this.classifier);
        }
        if (this.extension != null) {
            sb.append(':').append(this.extension);
        }
        return sb.toString();
    }
}
