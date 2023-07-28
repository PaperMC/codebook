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
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public abstract class LvtContextImpl<A extends AbstractInsnNode, H extends MemberData, AN>
        implements LvtContext<A, H, AN> {

    private final InsnContext.Method parent;
    private final A insn;
    private final H data;

    protected LvtContextImpl(final InsnContext.Method parent, final A insn, final H data) {
        this.parent = parent;
        this.insn = insn;
        this.data = data;
    }

    @Override
    public InsnContext.Method parent() {
        return this.parent;
    }

    @Override
    public A insn() {
        return this.insn;
    }

    @Override
    public H data() {
        return this.data;
    }

    public static final class MethodImpl extends LvtContextImpl<MethodInsnNode, MethodData, MethodNode>
            implements InsnContext.Method, Method {

        MethodImpl(final InsnContext.Method parent, final MethodInsnNode insn, final MethodData data) {
            super(parent, insn, data);
        }
    }

    public static final class FieldImpl extends LvtContextImpl<FieldInsnNode, FieldData, FieldNode>
            implements InsnContext.Field, Field {

        FieldImpl(final InsnContext.Method parent, final FieldInsnNode insn, final FieldData data) {
            super(parent, insn, data);
        }
    }
}
