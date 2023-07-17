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

import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.hydrate.SuperConstructorHydrator;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.mappings.ChangeChain;
import dev.denwav.hypo.mappings.MappingsCompletionManager;
import dev.denwav.hypo.mappings.contributors.CopyMappingsDown;
import dev.denwav.hypo.mappings.contributors.CopyRecordParameters;
import dev.denwav.hypo.model.ClassProviderRoot;
import dev.denwav.hypo.model.HypoModelUtil;
import io.papermc.codebook.exceptions.UnexpectedException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.cadixdev.lorenz.MappingSet;

public final class InspectJarPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpath;
    private final MappingSet mergedMappings;

    @Inject
    public InspectJarPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @Mappings final MappingSet mergedMappings) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.mergedMappings = mergedMappings;
    }

    @Override
    public void exec() {
        final ClassProviderRoot root;
        final List<ClassProviderRoot> ctxRoots;
        final ClassProviderRoot jdkRoot;
        try {
            root = ClassProviderRoot.fromJar(this.inputJar);
            ctxRoots = this.classpath.stream()
                    .map(HypoModelUtil.wrapFunction(ClassProviderRoot::fromJar))
                    .toList();
            jdkRoot = ClassProviderRoot.ofJdk();
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to resolve classpath roots", e);
        }

        final var context = HypoContext.builder()
                .withProvider(AsmClassDataProvider.of(root))
                .withContextProvider(AsmClassDataProvider.of(ctxRoots))
                .withContextProviders(AsmClassDataProvider.of(jdkRoot))
                .build();

        try (context) {
            final MappingSet mappings = this.inspect(context);
            this.bind(Mappings.KEY).to(mappings);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to inspect vanilla jar bytecode", e);
        }
    }

    private MappingSet inspect(final HypoContext context) throws IOException {
        HydrationManager.createDefault()
                .register(SuperConstructorHydrator.create())
                .hydrate(context);

        return ChangeChain.create()
                .addLink(CopyMappingsDown.create())
                .addLink(CopyRecordParameters.create())
                .applyChain(this.mergedMappings, MappingsCompletionManager.create(context));
    }
}
