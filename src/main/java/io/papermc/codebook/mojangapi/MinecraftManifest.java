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

import io.papermc.codebook.util.Downloader;
import java.util.List;
import java.util.Map;
import org.checkerframework.checker.nullness.qual.Nullable;

public record MinecraftManifest(Map<String, ?> latest, List<MinecraftVersion> versions) {

    private static final String URL = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private static @Nullable MinecraftManifest manifestInstance;

    public static MinecraftManifest getManifest() {
        if (manifestInstance != null) {
            return manifestInstance;
        }

        manifestInstance = Downloader.getJson(URL, MinecraftManifest.class);
        return manifestInstance;
    }
}
