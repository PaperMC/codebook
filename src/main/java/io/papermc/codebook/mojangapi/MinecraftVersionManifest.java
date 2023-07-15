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

package io.papermc.codebook.mojangapi;

import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.util.Downloader;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.checkerframework.checker.nullness.qual.Nullable;

public record MinecraftVersionManifest(Map<String, MinecraftVersionDownload> downloads) {

    private static final Map<String, MinecraftVersionManifest> instanceMap = new HashMap<>();

    public static MinecraftVersionManifest getManifestForVersion(
            final MinecraftManifest manifest, final String mcVersion) {
        return instanceMap.computeIfAbsent(mcVersion, k -> {
            final @Nullable MinecraftVersion version = manifest.versions().stream()
                    .filter(v -> v.id().equals(k))
                    .findFirst()
                    .orElse(null);

            if (version == null) {
                throw new UserErrorException("MC version not found: " + mcVersion);
            }

            return Downloader.getJson(version.url(), MinecraftVersionManifest.class);
        });
    }

    private MinecraftVersionDownload download(final String name) {
        return Objects.requireNonNull(
                this.downloads.get(name), "No such download '%s' in version manifest".formatted(name));
    }

    public MinecraftVersionDownload serverDownload() {
        return this.download("server");
    }

    public MinecraftVersionDownload serverMappingsDownload() {
        return this.download("server_mappings");
    }
}
