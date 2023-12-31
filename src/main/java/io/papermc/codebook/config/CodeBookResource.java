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

import java.nio.file.Path;

public sealed interface CodeBookResource extends CodeBookRemapper
        permits CodeBookCoordsResource, CodeBookFileResource, CodeBookUriResource {

    static CodeBookCoordsResourceBuilder ofMavenCoords() {
        return CodeBookCoordsResource.builder();
    }

    static CodeBookFileResource ofFile(final Path file) {
        return CodeBookFileResource.of(file);
    }

    static CodeBookUriResourceBuilder ofUri() {
        return CodeBookUriResource.builder();
    }

    Path resolveResourceFile(final Path tempDir);
}
