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
import static io.papermc.codebook.lvt.LvtUtil.isStringAllUppercase;
import static io.papermc.codebook.lvt.LvtUtil.toJvmType;

import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.MemberData;
import dev.denwav.hypo.model.data.types.JvmType;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.lvt.LvtTypeSuggester;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;

public class SingleVerbBooleanSuggester implements LvtSuggester {

    private final HypoContext hypoContext;

    @Inject
    SingleVerbBooleanSuggester(final HypoContext hypoContext) {
        this.hypoContext = hypoContext;
    }

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container)
            throws IOException {
        if (call.data().returnType() != PrimitiveType.BOOLEAN) {
            return null;
        }
        final String methodName = call.data().name();

        final String prefix;
        if (methodName.equals("is")) {
            prefix = "is";
        } else if (methodName.equals("has")) {
            prefix = "has";
        } else {
            return null;
        }

        final List<JvmType> paramTypes = call.data().params();
        if (paramTypes.size() != 1) {
            return null;
        }
        final String paramTypeDesc = paramTypes.get(0).asInternalName();

        final AbstractInsnNode prev = insn.node().getPrevious();
        if (prev instanceof final FieldInsnNode fieldInsnNode
                && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                && fieldInsnNode.name != null
                && isStringAllUppercase(fieldInsnNode.name)) {

            final boolean isFinal = Optional.ofNullable(
                            this.hypoContext.getContextProvider().findClass(fieldInsnNode.owner))
                    .map(fieldOwner -> fieldOwner.field(fieldInsnNode.name, toJvmType(fieldInsnNode.desc)))
                    .map(MemberData::isFinal)
                    .orElse(false);
            if (!isFinal) {
                return null;
            }

            return prefix + convertStaticFieldNameToLocalVarName(fieldInsnNode);
        } else {
            if ("Lnet/minecraft/tags/TagKey;".equals(paramTypeDesc)) { // isTag is better than isTagKey
                return "isTag";
            }
            final String typeName = LvtTypeSuggester.suggestNameFromType(this.hypoContext, toJvmType(paramTypeDesc));
            return prefix + capitalize(typeName, 0);
        }
    }

    private static String convertStaticFieldNameToLocalVarName(final FieldInsnNode fieldInsnNode) {
        final StringBuilder output = new StringBuilder();
        for (final String s : fieldInsnNode.name.split("_")) {
            output.append(s.charAt(0)).append(s.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return output.toString();
    }
}
