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

import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MemberData;
import dev.denwav.hypo.model.data.MethodData;
import io.papermc.codebook.lvt.suggestion.context.LvtContextImpl.FieldImpl;
import io.papermc.codebook.lvt.suggestion.context.LvtContextImpl.MethodImpl;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public interface LvtContext<A extends AbstractInsnNode, H extends MemberData, AN> extends InsnContext<H, AN> {

    static LvtContext.Method method(final InsnContext.Method parent, final MethodInsnNode insn, final MethodData data) {
        return new MethodImpl(parent, insn, data);
    }

    static LvtContext.Field field(final InsnContext.Method parent, final FieldInsnNode insn, final FieldData data) {
        return new FieldImpl(parent, insn, data);
    }

    InsnContext.Method parent();

    A insn();

    interface Method extends LvtContext<MethodInsnNode, MethodData, MethodNode> {}

    interface Field extends LvtContext<FieldInsnNode, FieldData, FieldNode> {}
}
