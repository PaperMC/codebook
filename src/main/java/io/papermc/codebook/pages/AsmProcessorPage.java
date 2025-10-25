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
import io.papermc.codebook.exceptions.UnexpectedException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public abstract class AsmProcessorPage extends CodeBookPage {

    protected final HypoContext context;

    protected AsmProcessorPage(final HypoContext context) {
        this.context = context;
    }

    @Override
    public void exec() {
        this.processClasses();
    }

    protected void processClasses() {
        final var tasks = new ArrayList<Future<?>>();
        for (final ClassData classData : this.context.getProvider().allClasses()) {
            final var task = this.context.getExecutor().submit(() -> {
                try {
                    this.processClass((AsmClassData) classData);
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
            throw new UnexpectedException("Failed to process classes", e.getCause());
        } catch (final InterruptedException e) {
            throw new UnexpectedException("Class processing interrupted", e);
        }
    }

    protected abstract void processClass(final AsmClassData classData) throws IOException;
}
