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

import static dev.denwav.hypo.model.ClassProviderRoot.fromJar;
import static dev.denwav.hypo.model.ClassProviderRoot.fromJars;
import static dev.denwav.hypo.model.ClassProviderRoot.ofJdk;

import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.hydrate.BridgeMethodHydrator;
import dev.denwav.hypo.asm.hydrate.LambdaCallHydrator;
import dev.denwav.hypo.asm.hydrate.LocalClassHydrator;
import dev.denwav.hypo.asm.hydrate.SuperConstructorHydrator;
import dev.denwav.hypo.core.HypoConfig;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import io.papermc.codebook.exceptions.UnexpectedException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import org.cadixdev.lorenz.MappingSet;

public final class InspectJarPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpathJars;
    private final HypoConfig config;

    @Inject
    public InspectJarPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpathJars,
            @Hypo final HypoConfig config) {
        this.inputJar = inputJar;
        this.classpathJars = classpathJars;
        this.config = config;
    }

    @Override
    public void exec() {
        final MappingSet lorenzMappings = this.loadMappings();

        final HypoContext ctx;

        try {
            ctx = HypoContext.builder()
                    .withProvider(AsmClassDataProvider.of(fromJar(this.inputJar)))
                    .withContextProvider(AsmClassDataProvider.of(fromJars(this.classpathJars.toArray(new Path[0]))))
                    .withContextProvider(AsmClassDataProvider.of(ofJdk()))
                    .withConfig(this.config)
                    .build();
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to open jar files", e);
        }

        this.bind(Hypo.KEY).to(ctx);

        try {
            HydrationManager.createDefault()
                    .register(BridgeMethodHydrator.create())
                    .register(SuperConstructorHydrator.create())
                    .register(LambdaCallHydrator.create())
                    .register(LocalClassHydrator.create())
                    .hydrate(ctx);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to hydrate data model", e);
        }
    }

    private MappingSet loadMappings() {
        return MappingSet.create();
    }
}
