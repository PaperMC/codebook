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
import dev.denwav.hypo.asm.AsmOutputWriter;
import dev.denwav.hypo.core.HypoContext;
import io.papermc.codebook.config.CodeBookClasspathResource;
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.config.CodeBookJarInput;
import io.papermc.codebook.config.CodeBookResource;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.pages.CodeBookPage;
import io.papermc.codebook.pages.ExtractVanillaJarPage;
import io.papermc.codebook.pages.FixJarPage;
import io.papermc.codebook.pages.InspectJarPage;
import io.papermc.codebook.pages.RemapJarPage;
import io.papermc.codebook.pages.RemapLvtPage;
import io.papermc.codebook.pages.UnpickPage;
import io.papermc.codebook.report.Reports;
import io.papermc.codebook.util.IOUtil;
import java.io.IOException;
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
                RemapJarPage.class,
                UnpickPage.class,
                InspectJarPage.class,
                FixJarPage.class,
                RemapLvtPage.class);

        Module module = this.createInitialModule(tempDir);
        for (final var page : book) {
            module = injector(module).getInstance(page).exec(module);
        }

        final HypoContext context = injector(module).getInstance(CodeBookPage.Hypo.KEY);
        final Path resultJar;
        try (context) {
            resultJar = tempDir.resolve("final_output.jar");
            AsmOutputWriter.to(resultJar).write(context);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to write output file", e);
        }

        IOUtil.move(resultJar, this.ctx.outputJar());
        if (this.ctx.reports() != null) {
            try {
                this.ctx.reports().generateReports();
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
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

        final @Nullable Path unpickDefinitions;
        if (this.ctx.unpickDefinitions() != null) {
            unpickDefinitions = this.ctx.unpickDefinitions().resolveResourceFile(tempDir);
        } else {
            unpickDefinitions = null;
        }

        final @Nullable Path constantsJar;
        if (this.ctx.constantsJar() != null) {
            constantsJar = this.ctx.constantsJar().resolveResourceFile(tempDir);
        } else {
            constantsJar = null;
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

                if (unpickDefinitions != null) {
                    this.bind(CodeBookPage.UnpickDefinitions.KEY).toInstance(unpickDefinitions);
                } else {
                    this.bind(CodeBookPage.UnpickDefinitions.KEY).toProvider(Providers.of(null));
                }

                if (constantsJar != null) {
                    this.bind(CodeBookPage.ConstantsJar.KEY).toInstance(constantsJar);
                } else {
                    this.bind(CodeBookPage.ConstantsJar.KEY).toProvider(Providers.of(null));
                }

                if (CodeBook.this.ctx.reports() != null) {
                    this.bind(CodeBookPage.Report.KEY).toInstance(CodeBook.this.ctx.reports());
                    this.install(CodeBook.this.ctx.reports());
                } else {
                    this.bind(CodeBookPage.Report.KEY).toInstance(Reports.NOOP);
                    this.install(Reports.NOOP);
                }
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
