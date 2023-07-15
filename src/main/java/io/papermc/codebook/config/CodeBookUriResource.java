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

import io.papermc.codebook.util.DownloadSpec;
import io.papermc.codebook.util.Downloader;
import java.net.URI;
import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;

public record CodeBookUriResource(String name, URI uri, @Nullable String sha1)
        implements CodeBookResource, DownloadSpec {

    @Override
    public Path resolveResourceFile(final Path tempDir) {
        final Path targetJar = tempDir.resolve("download/" + this.name);
        Downloader.downloadFile(this, targetJar);
        return targetJar;
    }
}
