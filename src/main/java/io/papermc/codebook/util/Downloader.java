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

import at.favre.lib.bytes.Bytes;
import com.google.gson.Gson;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.exceptions.UserErrorException;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class Downloader {

    public static final String FABRIC_MAVEN = "https://maven.fabricmc.net/";

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Gson gson = new Gson();

    private Downloader() {}

    public static URI getDownloadUri(final String mavenBaseUrl, final String mavenCoords) {
        final int extensionIndex = mavenCoords.indexOf('@');

        final String coords;
        if (extensionIndex == -1) {
            coords = mavenCoords;
        } else {
            coords = mavenCoords.substring(0, extensionIndex);
        }
        final String[] parts = coords.split(":");

        if (parts.length != 3 && parts.length != 4) {
            throw new UserErrorException("Invalid Maven coordinates: " + mavenCoords);
        }

        final var sb = new StringBuilder(mavenBaseUrl);
        if (!mavenBaseUrl.endsWith("/")) {
            sb.append('/');
        }
        sb.append(parts[0].replace('.', '/')).append('/');
        sb.append(parts[1]).append('/');
        sb.append(parts[2]).append('/');
        sb.append(parts[1]).append('-').append(parts[2]);

        if (parts.length == 4) {
            sb.append('-').append(parts[3]);
        }

        if (extensionIndex == -1) {
            sb.append(".jar");
        } else {
            sb.append('.').append(mavenCoords.substring(extensionIndex + 1));
        }
        return URI.create(sb.toString());
    }

    public static <T> T getJson(final String url, final Class<T> type) {
        final var request = HttpRequest.newBuilder(URI.create(url)).GET().build();

        try {
            final HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return gson.fromJson(response.body(), type);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to download json from " + url, e);
        }
    }

    public static void downloadFile(final DownloadSpec spec, final Path targetFile) {
        downloadFile(spec.uri(), spec.sha1(), targetFile);
    }

    public static void downloadFile(final URI uri, final Path targetFile) {
        downloadFile(uri, null, targetFile);
    }

    public static void downloadFile(final URI uri, final @Nullable String sha1, final Path targetFile) {
        final HttpRequest request = HttpRequest.newBuilder(uri).GET().build();

        IOUtil.createParentDirectories(targetFile);

        final Path file;
        try {
            final HttpResponse<Path> response = client.send(request, HttpResponse.BodyHandlers.ofFile(targetFile));
            file = response.body();

            if (response.statusCode() < 200 || response.statusCode() > 299) {
                throw new UnexpectedException(
                        "Failed to download file from " + uri + " - status code: " + response.statusCode());
            }
        } catch (final IOException | InterruptedException e) {
            throw new UnexpectedException("Failed to download file from " + uri, e);
        }

        if (sha1 == null) {
            return;
        }

        final String actualSha1 = Bytes.from(file.toFile()).hashSha1().encodeHex();
        if (!actualSha1.equalsIgnoreCase(sha1)) {
            throw new UnexpectedException("Downloaded file SHA-1 hash did not match expected hash");
        }
    }
}
