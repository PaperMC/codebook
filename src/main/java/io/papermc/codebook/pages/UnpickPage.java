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

import daomephsta.unpick.api.ConstantUninliner;
import daomephsta.unpick.api.classresolvers.ClassResolvers;
import daomephsta.unpick.api.classresolvers.IClassResolver;
import daomephsta.unpick.api.constantgroupers.ConstantGroupers;
import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;

public final class UnpickPage extends AsmProcessorPage {

    private final List<Path> classpath;
    private final @Nullable Path unpickDefinitions;
    private @MonotonicNonNull ConstantUninliner uninliner;

    @Inject
    public UnpickPage(
            @Hypo final HypoContext context,
            @ClasspathJars final List<Path> classpath,
            @UnpickDefinitions final @Nullable Path unpickDefinitions) {
        super(context);
        this.classpath = classpath;
        this.unpickDefinitions = unpickDefinitions;
    }

    @Override
    public void exec() {
        if (this.unpickDefinitions == null) {
            return;
        }

        boolean isZip;
        try (final ZipFile zf = new ZipFile(this.unpickDefinitions.toFile())) {
            isZip = true;
        } catch (final ZipException e) {
            isZip = false;
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        final IClassResolver classResolver = new IClassResolver() {
            @Override
            public @Nullable ClassReader resolveClass(final String internalName) {
                try {
                    final @Nullable ClassData cls =
                            UnpickPage.this.context.getContextProvider().findClass(internalName);
                    if (cls instanceof final AsmClassData asmClassData) {
                        // TODO - do something smarter here to avoid re-serializing classes all the time
                        final ClassWriter classWriter = new ClassWriter(0);
                        asmClassData.getNode().accept(classWriter);
                        return new ClassReader(classWriter.toByteArray());
                    }
                } catch (final IOException e) {
                    throw new UncheckedIOException(e);
                }
                return null;
            }
        };

        final List<ZipFile> zips = new ArrayList<>();

        if (isZip) {
            try (final FileSystem definitionsFs = FileSystems.newFileSystem(this.unpickDefinitions)) {
                final Path definitionsPath = definitionsFs.getPath("extras/definitions.unpick");
                this.unpick(definitionsPath, zips, classResolver);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        } else {
            try {
                this.unpick(this.unpickDefinitions, zips, classResolver);
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private void unpick(final Path definitionsPath, final List<ZipFile> zips, IClassResolver classResolver)
            throws IOException {
        try (final BufferedReader definitionsReader = Files.newBufferedReader(definitionsPath)) {
            for (final Path classpathJar : this.classpath) {
                final ZipFile zip = new ZipFile(classpathJar.toFile());
                zips.add(zip);
                classResolver = classResolver.chain(ClassResolvers.jar(zip));
            }

            classResolver = classResolver.chain(ClassResolvers.classpath());

            this.uninliner = ConstantUninliner.builder()
                    .grouper(ConstantGroupers.dataDriven()
                            .classResolver(classResolver)
                            .mappingSource(definitionsReader)
                            .build())
                    .classResolver(classResolver)
                    .build();

            this.processClasses();
        } finally {
            for (final ZipFile zip : zips) {
                try {
                    zip.close();
                } catch (final IOException e) {
                    // Ignore
                }
            }
        }
    }

    @Override
    protected void processClass(final AsmClassData classData) {
        if (this.uninliner == null) {
            return;
        }
        this.uninliner.transform(classData.getNode());
    }
}
