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

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
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
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.lvt.LvtNamer;
import jakarta.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.parchmentmc.feather.io.gson.MDCGsonAdapterFactory;
import org.parchmentmc.feather.io.gson.SimpleVersionAdapter;
import org.parchmentmc.feather.mapping.VersionedMappingDataContainer;
import org.parchmentmc.feather.util.SimpleVersion;

public final class RemapLvtPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path paramMappings;
    private final Path tempDir;
    private final CodeBookContext context;

    @Inject
    public RemapLvtPage(
            @InputJar final Path inputJar,
            @ClasspathJars final List<Path> classpath,
            @ParamMappings final Path paramMappings,
            @TempDir final Path tempDir,
            @Context final CodeBookContext context) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.paramMappings = paramMappings;
        this.tempDir = tempDir;
        this.context = context;
    }

    @Override
    public void exec() {
        final Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new MDCGsonAdapterFactory())
                .registerTypeAdapter(SimpleVersion.class, new SimpleVersionAdapter())
                .create();

        final VersionedMappingDataContainer mappings;
        try (final FileSystem fs = FileSystems.newFileSystem(this.paramMappings)) {
            final Path jsonFile = fs.getPath("/parchment.json");
            try (final BufferedReader reader = Files.newBufferedReader(jsonFile)) {
                mappings = gson.fromJson(reader, VersionedMappingDataContainer.class);
            }
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to read param mappings file", e);
        }

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

            final var lvtNamer = new LvtNamer(context, mappings);

            final Path result = this.remapLvtWithContext(context, lvtNamer);
            this.bind(InputJar.KEY).to(result);

            if (this.context.logMissingLvtSuggestions()) {
                final var comparator = Comparator.<Map.Entry<String, AtomicInteger>, Integer>comparing(
                        e -> e.getValue().get());
                lvtNamer.missedNameSuggestions.entrySet().stream()
                        .sorted(comparator.reversed())
                        .forEach(s -> System.out.println("missed: " + s.getKey() + " -- " + s.getValue() + " times"));
            }
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to remap LVT", e);
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

    private Path remapLvtWithContext(final HypoContext context, final LvtNamer lvtNamer) throws IOException {
        for (final ClassData classData : context.getProvider().allClasses()) {
            lvtNamer.processClass((AsmClassData) classData);
        }

        final Path lvtRemapped = this.tempDir.resolve("lvtRemapped.jar");
        AsmOutputWriter.to(lvtRemapped).write(context);

        this.bind(InputJar.KEY).to(lvtRemapped);
        return lvtRemapped;
    }
}
