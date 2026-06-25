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

package io.papermc.codebook.lvt.suggestion;

import static io.papermc.codebook.lvt.LvtUtil.staticFinalFieldNameToLocalName;
import static io.papermc.codebook.lvt.LvtUtil.toJvmType;

import io.papermc.codebook.lvt.suggestion.context.AssignmentContext;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.SuggesterContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class ComplexGetSuggester implements LvtSuggester {

    private static final StaticFieldEntry BLOCK_STATE_PROPERTY = new StaticFieldEntry(
            Set.of(
                    "net/minecraft/world/level/block/state/StateHolder",
                    "net/minecraft/world/level/block/state/BlockState",
                    "net/minecraft/world/level/block/state/BlockBehaviour$Properties",
                    "net/minecraft/world/level/material/FluidState"),
            Set.of(Map.entry(
                    "getValue", "(Lnet/minecraft/world/level/block/state/properties/Property;)Ljava/lang/Comparable;")),
            Set.of(
                    "Lnet/minecraft/world/level/block/state/properties/IntegerProperty;",
                    "Lnet/minecraft/world/level/block/state/properties/BooleanProperty;",
                    "Lnet/minecraft/world/level/block/state/properties/EnumProperty;",
                    "Lnet/minecraft/world/level/block/state/properties/DirectionProperty;"),
            "Value");

    private static final StaticFieldEntry LOOT_CONTEXT_PARAM = new StaticFieldEntry(
            Set.of(
                    "net/minecraft/world/level/storage/loot/LootContext",
                    "net/minecraft/world/level/storage/loot/LootParams$Builder"),
            Set.of(
                    Map.entry(
                            "getParamOrNull",
                            "(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;)Ljava/lang/Object;"),
                    Map.entry(
                            "getOptionalParameter",
                            "(Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;)Ljava/lang/Object;")),
            Set.of("Lnet/minecraft/world/level/storage/loot/parameters/LootContextParam;"),
            "Param");

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call,
            final MethodInsnContext insn,
            final ContainerContext container,
            final AssignmentContext assignment,
            final SuggesterContext suggester)
            throws IOException {
        final MethodInsnNode node = insn.node();
        if (BLOCK_STATE_PROPERTY.test(node)) {
            return BLOCK_STATE_PROPERTY.transform(node, assignment, suggester);
        }
        if (LOOT_CONTEXT_PARAM.test(node)) {
            return LOOT_CONTEXT_PARAM.transform(node, assignment, suggester);
        }
        return null;
    }

    private record StaticFieldEntry(
            Set<String> owners, Set<Entry<String, String>> methods, Set<String> fieldTypes, @Nullable String suffix) {

        boolean test(final MethodInsnNode node) {
            return matches(this.owners, this.methods, node);
        }

        String transform(
                final MethodInsnNode node, final AssignmentContext assignment, final SuggesterContext suggester)
                throws IOException {
            final AbstractInsnNode prev = node.getPrevious();
            if (prev instanceof final FieldInsnNode fieldInsnNode
                    && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                    && this.fieldTypes.contains(fieldInsnNode.desc)) {
                return staticFinalFieldNameToLocalName(fieldInsnNode.name) + (this.suffix == null ? "" : this.suffix);
            }
            // always use the type instead of any other suggesters
            return suggester.typeSuggester().suggestNameFromType(toJvmType(assignment.lvt().desc))
                    + (this.suffix == null ? "" : this.suffix);
        }
    }

    private static boolean matches(
            final Set<String> owners, final Set<Entry<String, String>> methods, final MethodInsnNode node) {
        return owners.contains(node.owner)
                && methods.stream()
                        .anyMatch(e ->
                                e.getKey().equals(node.name) && e.getValue().equals(node.desc));
    }
}
