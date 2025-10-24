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
import io.papermc.codebook.exceptions.UnexpectedException;
import io.papermc.codebook.lvt.LvtNamer;
import io.papermc.codebook.report.Reports;
import jakarta.inject.Inject;
import java.io.IOException;
import org.cadixdev.lorenz.MappingSet;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class RemapLvtPage extends AsmProcessorPage {

    private final @Nullable MappingSet paramMappings;
    private final Reports reports;
    private @Nullable LvtNamer lvtNamer;

    @Inject
    public RemapLvtPage(
            @Hypo final HypoContext hypoContext,
            @ParamMappings @Nullable final MappingSet paramMappings,
            @Report final Reports reports) {
        super(hypoContext);
        this.paramMappings = paramMappings;
        this.reports = reports;
    }

    @Override
    public void exec() {
        if (this.paramMappings == null) {
            return;
        }

        try {
            this.lvtNamer = new LvtNamer(this.context, this.paramMappings, this.reports);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create LVT namer", e);
        }

        this.processClasses();
    }

    @Override
    protected void processClass(final AsmClassData classData) throws IOException {
        if (this.lvtNamer != null) {
            this.lvtNamer.processClass(classData);
        }
    }
}
