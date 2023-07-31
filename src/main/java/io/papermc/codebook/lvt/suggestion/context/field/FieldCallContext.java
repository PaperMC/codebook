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

package io.papermc.codebook.lvt.suggestion.context.field;

import dev.denwav.hypo.asm.AsmFieldData;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.FieldData;
import org.objectweb.asm.tree.FieldNode;

public record FieldCallContext(FieldData data, ClassData parent, FieldNode node) {

    public static FieldCallContext create(final FieldData field) {
        return new FieldCallContext(field, field.parentClass(), ((AsmFieldData) field).getNode());
    }
}
