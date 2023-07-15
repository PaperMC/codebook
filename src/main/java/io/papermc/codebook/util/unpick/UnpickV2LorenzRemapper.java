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
import org.cadixdev.bombe.type.MethodDescriptor;
import org.cadixdev.bombe.type.Type;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.Mapping;
import org.checkerframework.checker.nullness.qual.Nullable;

/**
 * Copied from <a href="https://github.com/FabricMC/unpick/blob/95b5145abc9423001aaa26a9ad14b5da5623a697/unpick-format-utils/src/main/java/daomephsta/unpick/constantmappers/datadriven/parser/v2/UnpickV2Remapper.java">FabricMC unpick-format-utils</a>
 * But adapted to use with {@link org.cadixdev.lorenz.MappingSet}.
 */
public class UnpickV2LorenzRemapper implements UnpickV2Reader.Visitor {

    private final MappingSet mappings;
    private final UnpickV2Reader.Visitor delegate;

    public UnpickV2LorenzRemapper(final MappingSet mappings, final UnpickV2Reader.Visitor delegate) {
        this.mappings = mappings.copy();
        this.delegate = delegate;
    }

    private String remapClass(final String name) {
        return this.mappings
                .computeClassMapping(name)
                .map(Mapping::getDeobfuscatedName)
                .orElse(name);
    }

    private String remapMethod(final String owner, final String name, final String descriptor) {
        return this.mappings
                .computeClassMapping(owner)
                .flatMap(m -> m.getMethodMapping(name, descriptor))
                .map(Mapping::getDeobfuscatedName)
                .orElse(name);
    }

    private String remapField(final String owner, final String name) {
        return this.mappings
                .computeClassMapping(owner)
                .flatMap(m -> m.getFieldMapping(name))
                .map(Mapping::getDeobfuscatedName)
                .orElse(name);
    }

    private String remapMethodDescriptor(final String descriptor) {
        return this.mappings.deobfuscate(MethodDescriptor.of(descriptor)).toString();
    }

    private @Nullable String remapFieldDescriptor(final @Nullable String descriptor) {
        if (descriptor == null) {
            return null;
        }

        return this.mappings.deobfuscate(Type.of(descriptor)).toString();
    }

    public UnpickV2Reader.TargetMethodDefinitionVisitor visitTargetMethodDefinition(
            final String owner, final String name, final String descriptor) {
        final String remappedOwner = this.remapClass(owner);
        final String remappedName = this.remapMethod(owner, name, descriptor);
        final String remappedDescriptor = this.remapMethodDescriptor(descriptor);

        return this.delegate.visitTargetMethodDefinition(remappedOwner, remappedName, remappedDescriptor);
    }

    public void startVisit() {
        this.delegate.startVisit();
    }

    public void visitLineNumber(final int lineNumber) {
        this.delegate.visitLineNumber(lineNumber);
    }

    public void visitSimpleConstantDefinition(
            final String group, final String owner, final String name, final String value, final String descriptor) {
        final String remappedOwner = this.remapClass(owner);
        final String remappedName = this.remapField(owner, name);
        final @Nullable String remappedDescriptor = this.remapFieldDescriptor(descriptor);

        this.delegate.visitSimpleConstantDefinition(group, remappedOwner, remappedName, value, remappedDescriptor);
    }

    public void visitFlagConstantDefinition(
            final String group, final String owner, final String name, final String value, final String descriptor) {
        final String remappedOwner = this.remapClass(owner);
        final String remappedName = this.remapField(owner, name);
        final @Nullable String remappedDescriptor = this.remapFieldDescriptor(descriptor);

        this.delegate.visitFlagConstantDefinition(group, remappedOwner, remappedName, value, remappedDescriptor);
    }

    public void endVisit() {
        this.delegate.endVisit();
    }
}
