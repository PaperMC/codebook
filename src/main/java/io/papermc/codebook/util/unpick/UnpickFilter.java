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

package io.papermc.codebook.util.unpick;

import daomephsta.unpick.constantmappers.datadriven.parser.v2.UnpickV2Reader;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public class UnpickFilter implements UnpickV2Reader.Visitor {

    private final List<String> excludes;
    private final UnpickV2Reader.Visitor delegate;

    public UnpickFilter(final List<String> excludes, final UnpickV2Reader.Visitor delegate) {
        this.excludes = excludes;
        this.delegate = delegate;
    }

    @Override
    public void startVisit() {
        this.delegate.startVisit();
    }

    @Override
    public void visitLineNumber(final int lineNumber) {
        this.delegate.visitLineNumber(lineNumber);
    }

    @Override
    public void visitSimpleConstantDefinition(
            final String group, final String owner, final String name, final String value, final String descriptor) {
        if (this.isExcluded(owner)) {
            return;
        }
        this.delegate.visitSimpleConstantDefinition(group, owner, name, value, descriptor);
    }

    @Override
    public void visitFlagConstantDefinition(
            final String group, final String owner, final String name, final String value, final String descriptor) {
        if (this.isExcluded(owner)) {
            return;
        }
        this.delegate.visitFlagConstantDefinition(group, owner, name, value, descriptor);
    }

    @Override
    public UnpickV2Reader.@Nullable TargetMethodDefinitionVisitor visitTargetMethodDefinition(
            final String owner, final String name, final String descriptor) {
        if (this.isExcluded(owner)) {
            return null;
        }
        return this.delegate.visitTargetMethodDefinition(owner, name, descriptor);
    }

    @Override
    public void endVisit() {
        this.delegate.endVisit();
    }

    private boolean isExcluded(final String owner) {
        for (final String exclude : this.excludes) {
            if (owner.startsWith(exclude)) {
                return true;
            }
        }
        return false;
    }
}
