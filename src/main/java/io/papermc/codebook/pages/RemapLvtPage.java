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
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.HypoModelUtil;
import dev.denwav.hypo.model.data.ClassData;
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.lvt.LvtNamer;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import org.cadixdev.lorenz.MappingSet;

public final class RemapLvtPage extends CodeBookPage {

    private final HypoContext context;
    private final MappingSet paramMappings;
    private final boolean logMissingLvtSuggestions;

    @Inject
    public RemapLvtPage(
            @Hypo final HypoContext hypoContext,
            @ParamMappings final MappingSet paramMappings,
            @Context final CodeBookContext context) {
        this.context = hypoContext;
        this.paramMappings = paramMappings;
        this.logMissingLvtSuggestions = context.logMissingLvtSuggestions();
    }

    @Override
    public void exec() {
        final LvtNamer lvtNamer;
        try {
            lvtNamer = new LvtNamer(this.context, this.paramMappings);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create LVT namer", e);
        }

        this.remapLvt(lvtNamer);

        if (this.logMissingLvtSuggestions) {
            final var comparator = Comparator.<Map.Entry<String, AtomicInteger>, Integer>comparing(
                    e -> e.getValue().get());
            lvtNamer.missedNameSuggestions.entrySet().stream()
                    .sorted(comparator.reversed())
                    .forEach(s -> System.out.println("missed: " + s.getKey() + " -- " + s.getValue() + " times"));
        }
    }

    private void remapLvt(final LvtNamer lvtNamer) {
        final ArrayList<Future<?>> tasks = new ArrayList<>();
        for (final ClassData classData : this.context.getProvider().allClasses()) {
            final var task = this.context.getExecutor().submit(() -> {
                try {
                    lvtNamer.processClass((AsmClassData) classData);
                } catch (final Exception e) {
                    throw HypoModelUtil.rethrow(e);
                }
            });
            tasks.add(task);
        }

        try {
            for (final Future<?> task : tasks) {
                task.get();
            }
        } catch (final ExecutionException e) {
            throw new UnexpectedException("Failed to remap LVT", e.getCause());
        } catch (final InterruptedException e) {
            throw new UnexpectedException("LVT remap interrupted", e);
        }
    }
}
