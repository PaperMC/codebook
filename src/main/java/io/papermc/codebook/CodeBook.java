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

package io.papermc.codebook;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.util.Providers;
import io.papermc.codebook.config.CodeBookClasspathResource;
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.config.CodeBookJarInput;
import io.papermc.codebook.config.CodeBookResource;
import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.pages.CodeBookPage;
import io.papermc.codebook.pages.ExtractVanillaJarPage;
import io.papermc.codebook.pages.FixJarPage;
import io.papermc.codebook.pages.InspectJarPage;
import io.papermc.codebook.pages.MergeMappingsPage;
import io.papermc.codebook.pages.RemapJarPage;
import io.papermc.codebook.pages.UnpickPage;
import io.papermc.codebook.util.IOUtil;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class CodeBook {

    private final CodeBookContext ctx;

    public CodeBook(final CodeBookContext ctx) {
        this.ctx = ctx;
    }

    public void exec() {
        if (this.ctx.tempDir() == null) {
            final Path tempDir = IOUtil.createTempDir(".tmp_codebook");
            try {
                this.exec(tempDir);
            } finally {
                IOUtil.deleteRecursively(tempDir);
            }
        } else {
            this.exec(this.ctx.tempDir());
        }
    }

    private void exec(final Path tempDir) {
        this.deleteOutputFile();

        final var book = List.of(
                ExtractVanillaJarPage.class,
                MergeMappingsPage.class,
                InspectJarPage.class,
                RemapJarPage.class,
                FixJarPage.class,
                UnpickPage.class);

        Module module = this.createInitialModule(tempDir);
        for (final var page : book) {
            module = injector(module).getInstance(page).exec(module);
        }

        final Path resultJar = injector(module).getInstance(CodeBookPage.InputJar.KEY);
        IOUtil.move(resultJar, this.ctx.outputJar());
    }

    private static Injector injector(final Module module) {
        return Guice.createInjector(module);
    }

    private Module createInitialModule(final Path tempDir) {
        final @Nullable CodeBookResource mappings = this.ctx.input().resolveMappings(this.ctx, tempDir);
        if (mappings == null) {
            throw new IllegalStateException("No mappings file could be determined for the given configuration");
        }

        final Path inputJar = this.ctx.input().resolveInputFile(tempDir);
        final @Nullable List<Path> classpathJars;
        if (this.ctx.input() instanceof final CodeBookJarInput input) {
            classpathJars = input.classpathJars();
        } else {
            classpathJars = null;
        }

        final Path mappingsFile = mappings.resolveResourceFile(tempDir);
        final @Nullable Path paramMappingsFile;
        if (this.ctx.paramMappings() != null) {
            paramMappingsFile = this.ctx.paramMappings().resolveResourceFile(tempDir);
        } else {
            paramMappingsFile = null;
        }

        final List<Path> remapperJars;
        if (this.ctx.remapperJar() instanceof final CodeBookResource resource) {
            remapperJars = List.of(resource.resolveResourceFile(tempDir));
        } else if (this.ctx.remapperJar() instanceof final CodeBookClasspathResource resource) {
            remapperJars = resource.jars();
        } else {
            throw new LinkageError();
        }

        return new AbstractModule() {
            @Override
            protected void configure() {
                this.bind(CodeBookPage.Context.KEY).toInstance(CodeBook.this.ctx);
                this.bind(CodeBookPage.InputJar.KEY).toInstance(inputJar);
                if (classpathJars != null) {
                    this.bind(CodeBookPage.ClasspathJars.KEY).toInstance(classpathJars);
                } else {
                    this.bind(CodeBookPage.ClasspathJars.KEY).toProvider(Providers.of(null));
                }
                this.bind(CodeBookPage.TempDir.KEY).toInstance(tempDir);
                this.bind(CodeBookPage.MojangMappings.PATH_KEY).toInstance(mappingsFile);
                this.bind(CodeBookPage.ParamMappings.PATH_KEY).toProvider(Providers.of(paramMappingsFile));
                this.bind(CodeBookPage.RemapperJar.KEY).toInstance(remapperJars);
            }
        };
    }

    private void deleteOutputFile() {
        if (Files.isRegularFile(this.ctx.outputJar())) {
            if (!this.ctx.overwrite()) {
                throw new UserErrorException("Cannot write output file " + this.ctx.outputJar()
                        + " as it already exists, and --force was not specified.");
            }

            IOUtil.deleteIfExists(this.ctx.outputJar());
        }
    }
}
