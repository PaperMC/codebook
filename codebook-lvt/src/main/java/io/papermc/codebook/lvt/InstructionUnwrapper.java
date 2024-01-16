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

package io.papermc.codebook.lvt;

import com.google.inject.Injector;
import io.papermc.codebook.report.ReportType;
import io.papermc.codebook.report.Reports;
import io.papermc.codebook.report.type.CheckCastWraps;
import java.util.Set;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

public class InstructionUnwrapper {

    private final Reports reports;
    private final Injector reportsInjector;

    private static final MethodMatcher BOX_METHODS = new MethodMatcher(Set.of(
            new Method("java/lang/Byte", "byteValue", "()B"),
            new Method("java/lang/Short", "shortValue", "()S"),
            new Method("java/lang/Integer", "intValue", "()I"),
            new Method("java/lang/Long", "longValue", "()J"),
            new Method("java/lang/Float", "floatValue", "()F"),
            new Method("java/lang/Double", "doubleValue", "()D"),
            new Method("java/lang/Boolean", "booleanValue", "()Z"),
            new Method("java/lang/Character", "charValue", "()C")));

    private static final MethodMatcher UNWRAP_AFTER_CAST = new MethodMatcher(Set.of(
            new Method(
                    "net/minecraft/world/level/block/state/BlockState",
                    "getValue",
                    "(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;"),
            new Method(
                    "net/minecraft/world/level/storage/loot/LootContext",
                    "getParamOrNull",
                    "(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;)Ljava/lang/Object;"),
            new Method(
                    "net/minecraft/world/level/storage/loot/LootParams$Builder",
                    "getOptionalParameter",
                    "(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;)Ljava/lang/Object;")));

    public InstructionUnwrapper(final Reports reports, final Injector reportsInjector) {
        this.reports = reports;
        this.reportsInjector = reportsInjector;
    }

    public @Nullable AbstractInsnNode unwrapFromAssignment(final VarInsnNode assignment) {
        @Nullable AbstractInsnNode prev = assignment.getPrevious();
        if (prev == null) {
            return null;
        }

        // unwrap unboxing methods and the subsequent checkcast to the boxed type
        if (prev.getOpcode() == Opcodes.INVOKEVIRTUAL && BOX_METHODS.matches(prev)) {
            prev = prev.getPrevious();
            if (prev != null && prev.getOpcode() == Opcodes.CHECKCAST) {
                prev = prev.getPrevious();
            }
        }
        if (prev == null) {
            return null;
        }

        if (prev.getOpcode() == Opcodes.CHECKCAST) {
            final AbstractInsnNode tempPrev = prev.getPrevious();
            if (tempPrev.getOpcode() == Opcodes.INVOKEVIRTUAL
                    || tempPrev.getOpcode() == Opcodes.INVOKEINTERFACE
                    || tempPrev.getOpcode() == Opcodes.INVOKESTATIC) {
                final MethodInsnNode methodInsn = (MethodInsnNode) tempPrev;
                if (UNWRAP_AFTER_CAST.matches(methodInsn)) {
                    prev = methodInsn;
                } else {
                    if (this.reports.shouldGenerate(ReportType.CHECK_CAST_WRAPS)) {
                        this.reportsInjector.getInstance(CheckCastWraps.class).report(methodInsn);
                    }
                    return null;
                }
            }
        }

        return prev;
    }

    private record MethodMatcher(Set<Method> methods, Set<String> methodNames) {

        private MethodMatcher(final Set<Method> methods) {
            this(methods, methods.stream().map(Method::name).collect(Collectors.toUnmodifiableSet()));
        }

        boolean matches(final AbstractInsnNode insn) {
            return insn instanceof final MethodInsnNode methodInsnNode
                    && this.methodNames.contains(methodInsnNode.name)
                    && this.methods.stream().anyMatch(m -> m.matches(methodInsnNode));
        }
    }

    private record Method(String owner, String name, String desc, boolean itf) {

        private Method(final String owner, final String name, final String desc) {
            this(owner, name, desc, false);
        }

        boolean matches(final MethodInsnNode insn) {
            return this.owner.equals(insn.owner)
                    && this.name.equals(insn.name)
                    && this.desc.equals(insn.desc)
                    && this.itf == insn.itf;
        }
    }
}
