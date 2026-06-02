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

import io.papermc.codebook.mojangapi.MinecraftManifest;
import io.papermc.codebook.mojangapi.MinecraftVersionManifest;
import io.papermc.codebook.util.Downloader;
import java.nio.file.Path;

public record CodeBookVersionInput(String mcVersion) implements CodeBookInput {

    static CodeBookVersionInput of(final String mcVersion) {
        return new CodeBookVersionInput(mcVersion);
    }

    @Override
    public Path resolveInputFile(final Path tempDir) {
        final var manifest = MinecraftManifest.getManifest();
        final var versionManifest = MinecraftVersionManifest.getManifestForVersion(manifest, this.mcVersion);

        final Path targetJar = tempDir.resolve("download/server.jar");
        Downloader.downloadFile(versionManifest.serverDownload(), targetJar);
        return targetJar;
    }
}
