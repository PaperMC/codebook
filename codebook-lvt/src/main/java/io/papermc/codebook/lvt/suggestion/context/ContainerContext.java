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

package io.papermc.codebook.lvt.suggestion.context;

import dev.denwav.hypo.asm.AsmConstructorData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import org.objectweb.asm.tree.MethodNode;

public record ContainerContext(MethodData method, ClassData parent, MethodNode node) {

    public static ContainerContext from(final MethodData method) {
        return new ContainerContext(method, method.parentClass(), fromHypo(method));
    }

    public static MethodNode fromHypo(final MethodData method) {
        if (method instanceof final AsmMethodData asmMethodData) {
            return asmMethodData.getNode();
        } else if (method instanceof final AsmConstructorData asmConstructorData) {
            return asmConstructorData.getNode();
        } else {
            throw new IllegalArgumentException();
        }
    }
}
