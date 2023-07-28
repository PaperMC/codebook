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

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmConstructorData;
import dev.denwav.hypo.asm.AsmFieldData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MemberData;
import dev.denwav.hypo.model.data.MethodData;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public interface InsnContext<H extends MemberData, A> {

    static Method method(final MethodData data) {
        return new MethodInsnContextImpl(data);
    }

    default AsmClassData owner() {
        return ((AsmClassData) this.data().parentClass());
    }

    H data();

    A node();

    interface Method extends InsnContext<MethodData, MethodNode> {

        @Override
        default MethodNode node() {
            if (this.data() instanceof final AsmConstructorData asmConstructorData) {
                return asmConstructorData.getNode();
            } else if (this.data() instanceof final AsmMethodData asmMethodData) {
                return asmMethodData.getNode();
            } else {
                throw new IllegalStateException();
            }
        }
    }

    interface Field extends InsnContext<FieldData, FieldNode> {

        @Override
        default FieldNode node() {
            return ((AsmFieldData) this.data()).getNode();
        }
    }
}
