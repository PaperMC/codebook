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

import static io.papermc.codebook.lvt.LvtUtil.capitalize;
import static io.papermc.codebook.lvt.LvtUtil.decapitalize;
import static io.papermc.codebook.lvt.LvtUtil.prevInsnIgnoringConvertCast;
import static java.util.Objects.requireNonNull;

import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.lvt.suggestion.context.AssignmentContext;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.SuggesterContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public class PositionsSuggester implements LvtSuggester {

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call,
            final MethodInsnContext insn,
            final ContainerContext container,
            final AssignmentContext assignment,
            final SuggesterContext suggester)
            throws IOException {
        if ("net/minecraft/core/SectionPos".equals(insn.owner().name())) {
            return suggestNameForSectionPos(container.node(), call.data(), insn.node());
        } else if ("net/minecraft/core/QuartPos".equals(insn.owner().name())) {
            return suggestNameForQuartPos(container.node(), call.data(), insn.node());
        } else if ("net/minecraft/core/BlockPos".equals(insn.owner().name())) {
            return suggestNameForBlockPos(call.data());
        } else if ("net/minecraft/world/level/ChunkPos".equals(insn.owner().name())) {
            return suggestNameForChunkPos(call.data());
        }
        return null;
    }

    enum PosType {
        BLOCK("block", "blockpos"),
        QUART("quart", "quartpos", "biome", "biomepos"),
        SECTION("section", "sectionpos");

        final Set<String> possibleNames;
        final String localName;

        PosType(final String... possibleNames) {
            this.possibleNames = Set.of(possibleNames);
            this.localName = this.name().toLowerCase(Locale.ENGLISH) + "Pos";
        }
    }

    private record MethodConfig(PosType returnType, PosType paramType, String prefix) {

        private MethodConfig(final PosType returnType, final PosType paramType) {
            this(returnType, paramType, "");
        }

        String varName(final String suffix) {
            if (this.prefix.isEmpty()) {
                return this.returnType.localName + suffix;
            } else {
                return this.prefix + capitalize(this.returnType.localName, 0) + suffix;
            }
        }
    }

    private static @Nullable String suggestNameForSectionPos(
            final MethodNode enclosingMethodNode, final MethodData method, final MethodInsnNode insn) {
        // this matches 2 methods for each x, y, z. One static that takes the packed position, the output names are
        // appropriate for both method types
        final @Nullable String possibleSimpleName =
                switch (method.name()) {
                    case "x" -> "sectionX";
                    case "y" -> "sectionY";
                    case "z" -> "sectionZ";
                    case "blockToSection", "asLong" -> "packedSectionPos";
                    default -> null;
                };
        if (possibleSimpleName != null) {
            return possibleSimpleName;
        }

        final @Nullable MethodConfig methodConfig =
                switch (method.name()) {
                    case "blockToSectionCoord", "posToSectionCoord" -> new MethodConfig(PosType.SECTION, PosType.BLOCK);
                    case "sectionToBlockCoord" -> new MethodConfig(PosType.BLOCK, PosType.SECTION);
                    case "sectionRelative" -> new MethodConfig(PosType.BLOCK, PosType.BLOCK, "relative");
                    default -> null;
                };

        return getCoordLocalNameFromMethodPair(enclosingMethodNode, insn, method, methodConfig);
    }

    private static @Nullable String suggestNameForQuartPos(
            final MethodNode enclosingMethodNode, final MethodData method, final MethodInsnNode insn) {
        // all methods in QuartPos have a single int param and return int
        if (method.params().size() != 1
                || method.param(0) != PrimitiveType.INT
                || method.returnType() != PrimitiveType.INT) {
            return null;
        }

        final @Nullable MethodConfig methodConfig =
                switch (method.name()) {
                    case "fromBlock" -> new MethodConfig(PosType.QUART, PosType.BLOCK);
                    case "toBlock" -> new MethodConfig(PosType.BLOCK, PosType.QUART);
                    case "fromSection" -> new MethodConfig(PosType.QUART, PosType.SECTION);
                    case "toSection" -> new MethodConfig(PosType.SECTION, PosType.QUART);
                    default -> null;
                };
        if (methodConfig == null) {
            return null;
        }

        return getCoordLocalNameFromMethodPair(enclosingMethodNode, insn, method, methodConfig);
    }

    private static @Nullable String suggestNameForBlockPos(final MethodData method) {
        final String suggestion;
        if (method.name().equals("asLong")) {
            suggestion = "packedBlockPos";
        } else if (method.isStatic() && method.name().equals("offset") && method.returnType() == PrimitiveType.LONG) {
            suggestion = "offsetPackedBlockPos";
        } else {
            return null;
        }
        return suggestion;
    }

    private static @Nullable String suggestNameForChunkPos(final MethodData method) {
        final String suggestion;
        if (method.name().equals("asLong") || method.name().equals("toLong")) {
            suggestion = "packedChunkPos";
        } else {
            return null;
        }
        return suggestion;
    }

    private static final String[] COMMON_PERSISTENT_PREFIXES = new String[] {"min", "max"};

    private static @Nullable String getCoordLocalNameFromMethodPair(
            final MethodNode enclosingMethodNode,
            final MethodInsnNode insn,
            final MethodData method,
            final @Nullable MethodConfig methodConfig) {
        if (methodConfig == null) {
            return null;
        }

        if (method.params().size() != 1) {
            // add "Coord" since we don't know if its x, y, or z and more
            // than 1 param makes it too complex to figure out
            return methodConfig.varName("Coord");
        }

        final AbstractInsnNode prev = requireNonNull(prevInsnIgnoringConvertCast(insn));
        @Nullable String suggestion = null;
        if (prev instanceof final VarInsnNode varNode) {
            final LocalVariableNode paramVarNode = findLocalVar(enclosingMethodNode, insn, varNode.var);
            suggestion = suggestSpecificCoordName(methodConfig, paramVarNode.name, COMMON_PERSISTENT_PREFIXES);
        } else if (prev instanceof final MethodInsnNode methodNode) {
            final @Nullable String strippedName =
                    methodNode.name.startsWith("get") ? decapitalize(methodNode.name.substring(3)) : methodNode.name;
            if (strippedName != null) {
                suggestion = suggestSpecificCoordName(methodConfig, strippedName, COMMON_PERSISTENT_PREFIXES);
            }
        } else if (prev instanceof final FieldInsnNode fieldNode && fieldNode.getOpcode() == Opcodes.GETFIELD) {
            suggestion = suggestSpecificCoordName(methodConfig, fieldNode.name, COMMON_PERSISTENT_PREFIXES);
        }
        return Objects.requireNonNullElseGet(
                suggestion, () -> methodConfig.varName("Coord")); // add "Coord" since we don't know if its x, y, or z
    }

    private static @Nullable String suggestSpecificCoordName(
            final MethodConfig methodConfig, final String fullName, final String... persistentPrefixes) {
        String prefix = "";
        if (fullName.length() > 1) {
            for (final String persistentPrefix : persistentPrefixes) {
                if (fullName.startsWith(persistentPrefix)) {
                    prefix = persistentPrefix;
                    break;
                }
            }
        }
        final String nameWithoutPrefix = fullName.substring(prefix.length());
        final int possibleCoordIdx = getPossibleCoordIdx(nameWithoutPrefix);
        if (possibleCoordIdx > -1
                && (nameWithoutPrefix.length() == 1
                        || methodConfig.paramType.possibleNames.contains(
                                nameWithoutPrefix.substring(0, possibleCoordIdx).toLowerCase(Locale.ENGLISH)))) {
            return methodConfig.varName(
                    capitalize(prefix, 0) + Character.toUpperCase(nameWithoutPrefix.charAt(possibleCoordIdx)));
        }
        return null;
    }

    private static int getPossibleCoordIdx(final String name) {
        for (int i = name.length() - 1; i >= 0; i--) {
            final char ch = name.charAt(i);
            if (!Character.isAlphabetic(ch)) {
                continue;
            }
            if (isCoord(ch)) {
                return i;
            }
            return -1;
        }
        return -1; // don't think this is possible
    }

    private static boolean isCoord(final char ch) {
        return ch == 'X' || ch == 'Y' || ch == 'Z' || ch == 'x' || ch == 'y' || ch == 'z';
    }

    private static LocalVariableNode findLocalVar(
            final MethodNode enclosingMethod, final AbstractInsnNode insn, final int varIdx) {
        final List<LocalVariableNode> matching = new ArrayList<>();
        for (final LocalVariableNode lvn : requireNonNull(enclosingMethod.localVariables)) {
            if (lvn.index == varIdx) {
                matching.add(lvn);
            }
        }
        if (matching.isEmpty()) {
            throw new IllegalStateException("Cannot find idx " + varIdx + " on " + enclosingMethod.name + " "
                    + enclosingMethod.desc + " (no match)");
        } else if (matching.size() == 1) {
            return matching.get(0);
        } else {
            @Nullable AbstractInsnNode prev = insn.getPrevious();
            if (prev == null) {
                throw new IllegalStateException("Cannot find idx " + varIdx + " on " + enclosingMethod.name + " "
                        + enclosingMethod.desc + " (multiple matches)");
            }
            while (true) {
                while (!(prev instanceof final LabelNode labelNode)) {
                    prev = prev.getPrevious();
                }
                for (final LocalVariableNode match : matching) {
                    if (match.start.getLabel() == labelNode.getLabel()) {
                        return match;
                    }
                }
                prev = prev.getPrevious();
            }
        }
    }
}
