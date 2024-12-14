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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.hydrate.BridgeMethodHydrator;
import dev.denwav.hypo.asm.hydrate.LambdaCallHydrator;
import dev.denwav.hypo.asm.hydrate.LocalClassHydrator;
import dev.denwav.hypo.asm.hydrate.SuperConstructorHydrator;
import dev.denwav.hypo.core.HypoConfig;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.mappings.ChangeChain;
import dev.denwav.hypo.mappings.MappingsCompletionManager;
import dev.denwav.hypo.mappings.contributors.CopyLambdaParametersDown;
import dev.denwav.hypo.mappings.contributors.CopyMappingsDown;
import dev.denwav.hypo.mappings.contributors.CopyRecordParameters;
import io.papermc.codebook.exceptions.UnexpectedException;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.cadixdev.bombe.type.signature.FieldSignature;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.mapping.MappingDataContainer;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

public final class InspectJarPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpathJars;
    private final @Nullable Path paramMappings;
    private final HypoConfig config;

    @Inject
    public InspectJarPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpathJars,
            @ParamMappings @Nullable final Path paramMappings,
            @Hypo final HypoConfig config) {
        this.inputJar = inputJar;
        this.classpathJars = classpathJars;
        this.paramMappings = paramMappings;
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

        if (this.paramMappings != null) {
            // Fill in any missing mapping information
            final MappingSet completedMappings = ChangeChain.create()
                    .addLink(
                            CopyMappingsDown.createWithoutOverwrite(),
                            CopyLambdaParametersDown.createWithoutOverwrite(),
                            CopyRecordParameters.create())
                    .applyChain(lorenzMappings, MappingsCompletionManager.create(ctx));

            this.bind(ParamMappings.KEY).to(completedMappings);
        } else {
            this.bind(ParamMappings.KEY).to(null);
        }
    }

    private MappingSet loadMappings() {
        if (this.paramMappings == null) {
            return MappingSet.create();
        }

        final Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                .create();

        try (final FileSystem fs = FileSystems.newFileSystem(this.paramMappings)) {
            final Path jsonFile = fs.getPath("/parchment.json");
            try (final BufferedReader reader = Files.newBufferedReader(jsonFile)) {
                return this.toLorenz(gson.fromJson(reader, VersionedMappingDataContainer.class));
            }
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to read param mappings file", e);
        }
    }

    private MappingSet toLorenz(final MappingDataContainer container) {
        final MappingSet mappings = MappingSet.create();

        for (final MappingDataContainer.ClassData aClass : container.getClasses()) {
            final ClassMapping<?, ?> classMapping = mappings.getOrCreateClassMapping(aClass.getName());
            for (final MappingDataContainer.MethodData method : aClass.getMethods()) {
                final MethodMapping methodMapping =
                        classMapping.getOrCreateMethodMapping(method.getName(), method.getDescriptor());
                for (final MappingDataContainer.ParameterData param : method.getParameters()) {
                    methodMapping.getOrCreateParameterMapping(param.getIndex()).setDeobfuscatedName(param.getName());
                }
            }
            for (final MappingDataContainer.FieldData field : aClass.getFields()) {
                classMapping.getOrCreateFieldMapping(FieldSignature.of(field.getName(), field.getDescriptor()));
            }
        }

        return mappings;
    }
}
