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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Module;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StripSignaturesPageTest {

    private static final Map<String, String> CREATE_FS_ARGS = Map.of("create", "true");

    @TempDir
    Path tempDir;

    @Test
    void stripsSignatureFilesAndManifestDigests() throws IOException {
        final Path inputJar = this.tempDir.resolve("input.jar");
        createJar(
                inputJar,
                Map.of(
                        "/META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\n\n"
                                + "Name: com/example/Test.class\n"
                                + "SHA-256-Digest: digest\n"
                                + "Magic: keep\n\n"
                                + "Name: com/example/Empty.class\n"
                                + "SHA1-Digest: digest\n\n",
                        "/META-INF/TEST.SF",
                        "signature",
                        "/META-INF/TEST.RSA",
                        "block",
                        "/META-INF/nested/TEST.EC",
                        "nested block",
                        "/META-INF/SIG-CODEBOOK",
                        "should remain",
                        "/meta-inf/LOWER.SF",
                        "should remain",
                        "/META-INF/services/test",
                        "service",
                        "/com/example/Test.class",
                        "class bytes"));

        final Path rewrittenJar = execPage(inputJar, this.tempDir);
        final Manifest manifest = readManifest(rewrittenJar);

        assertNotEquals(inputJar, rewrittenJar);
        assertEquals(
                Set.of(
                        "/META-INF/MANIFEST.MF",
                        "/META-INF/SIG-CODEBOOK",
                        "/META-INF/services/test",
                        "/com/example/Test.class",
                        "/meta-inf/LOWER.SF"),
                jarEntries(rewrittenJar));
        assertEquals(
                Set.of(
                        "/META-INF/MANIFEST.MF",
                        "/META-INF/SIG-CODEBOOK",
                        "/META-INF/TEST.SF",
                        "/META-INF/TEST.RSA",
                        "/META-INF/nested/TEST.EC",
                        "/META-INF/services/test",
                        "/com/example/Test.class",
                        "/meta-inf/LOWER.SF"),
                jarEntries(inputJar));
        assertEquals("keep", manifest.getEntries().get("com/example/Test.class").getValue("Magic"));
        assertNull(manifest.getEntries().get("com/example/Test.class").getValue("SHA-256-Digest"));
        assertNull(manifest.getEntries().get("com/example/Empty.class"));
    }

    @Test
    void rewritesJarWhenOnlyManifestContainsDigestEntries() throws IOException {
        final Path inputJar = this.tempDir.resolve("manifest-only.jar");
        createJar(
                inputJar,
                Map.of(
                        "/META-INF/MANIFEST.MF",
                        "Manifest-Version: 1.0\n\n"
                                + "Name: com/example/Test.class\n"
                                + "SHA-256-Digest: digest\n"
                                + "Magic: keep\n\n",
                        "/com/example/Test.class",
                        "class bytes"));

        final Path rewrittenJar = execPage(inputJar, this.tempDir);
        final Manifest manifest = readManifest(rewrittenJar);

        assertNotEquals(inputJar, rewrittenJar);
        assertEquals("keep", manifest.getEntries().get("com/example/Test.class").getValue("Magic"));
        assertNull(manifest.getEntries().get("com/example/Test.class").getValue("SHA-256-Digest"));
    }

    @Test
    void keepsOriginalJarWhenNoSignatureFilesExist() throws IOException {
        final Path inputJar = this.tempDir.resolve("unsigned.jar");
        createJar(
                inputJar,
                Map.of(
                        "/META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n",
                        "/META-INF/services/test", "service",
                        "/com/example/Test.class", "class bytes"));

        final Path rewrittenJar = execPage(inputJar, this.tempDir);

        assertEquals(inputJar, rewrittenJar);
    }

    private static Path execPage(final Path inputJar, final Path tempDir) {
        final StripSignaturesPage page = new StripSignaturesPage(inputJar, tempDir);
        final Module module = page.exec(new AbstractModule() {
            @Override
            protected void configure() {
                this.bind(CodeBookPage.InputJar.KEY).toInstance(inputJar);
            }
        });
        return Guice.createInjector(module).getInstance(CodeBookPage.InputJar.KEY);
    }

    private static void createJar(final Path jar, final Map<String, String> entries) throws IOException {
        try (final FileSystem fs = FileSystems.newFileSystem(jar, CREATE_FS_ARGS)) {
            for (final var entry : entries.entrySet()) {
                final Path path = fs.getPath(entry.getKey());
                Files.createDirectories(path.getParent());
                Files.writeString(path, entry.getValue());
            }
        }
    }

    private static Set<String> jarEntries(final Path jar) throws IOException {
        try (final FileSystem fs = FileSystems.newFileSystem(jar)) {
            try (final var paths = Files.walk(fs.getPath("/"))) {
                return paths.filter(Files::isRegularFile)
                        .map(Path::toString)
                        .collect(java.util.stream.Collectors.toSet());
            }
        }
    }

    private static Manifest readManifest(final Path jar) throws IOException {
        try (final FileSystem fs = FileSystems.newFileSystem(jar)) {
            try (final var input = Files.newInputStream(fs.getPath("/META-INF/MANIFEST.MF"))) {
                return new Manifest(input);
            }
        }
    }
}
