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

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.AsmOutputWriter;
import dev.denwav.hypo.asm.hydrate.LambdaCallHydrator;
import dev.denwav.hypo.asm.hydrate.LocalClassHydrator;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.model.ClassProviderRoot;
import dev.denwav.hypo.model.HypoModelUtil;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.lvt.LvtNamer;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.cadixdev.lorenz.MappingSet;

public final class RemapLvtPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpath;
    private final MappingSet mappings;
    private final Path tempDir;

    @Inject
    public RemapLvtPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @Mappings final MappingSet mappings,
            @TempDir final Path tempDir) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.mappings = mappings.reverse();
        this.tempDir = tempDir;
    }

    @Override
    public void exec() {
        final HypoContext context;
        try {
            context = this.createContext();
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create context for bytecode analysis", e);
        }

        try (context) {
            HydrationManager.createDefault()
                    .register(LambdaCallHydrator.create())
                    .register(LocalClassHydrator.create())
                    .hydrate(context);

            final Path result = this.remapLvtWithContext(context);
            this.bind(InputJar.KEY).to(result);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to fix jar", e);
        }
    }

    private HypoContext createContext() throws IOException {
        return HypoContext.builder()
                .withProvider(AsmClassDataProvider.of(ClassProviderRoot.fromJar(this.inputJar)))
                .withContextProvider(AsmClassDataProvider.of(this.classpath.stream()
                        .map(HypoModelUtil.wrapFunction(ClassProviderRoot::fromJar))
                        .toList()))
                .withContextProvider(AsmClassDataProvider.of(ClassProviderRoot.ofJdk()))
                .build();
    }

    private Path remapLvtWithContext(final HypoContext context) throws IOException {
        for (final ClassData classData : context.getProvider().allClasses()) {
            this.processClass(context, (AsmClassData) classData);
        }

        final Path lvtRemapped = this.tempDir.resolve("lvtRemapped.jar");
        AsmOutputWriter.to(lvtRemapped).write(context);

        this.bind(InputJar.KEY).to(lvtRemapped);
        return lvtRemapped;
    }

    private void processClass(final HypoContext context, final AsmClassData classData) throws IOException {
        for (final MethodData method : classData.methods()) {
            LvtNamer.fillNames(context, method, this.mappings);
        }
    }
}
