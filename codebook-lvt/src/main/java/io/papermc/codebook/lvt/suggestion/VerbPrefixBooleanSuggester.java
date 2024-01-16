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

import static io.papermc.codebook.lvt.LvtUtil.tryMatchPrefix;

import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.lvt.suggestion.context.AssignmentContext;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.SuggesterContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

/*
This matches against methods that return booleans with prefixes that suggest the full method name,
including the prefix should be used as the local variable name.
 */
public class VerbPrefixBooleanSuggester implements LvtSuggester {

    private static final List<String> BOOL_METHOD_PREFIXES = List.of("is", "has", "can", "should");

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container, final AssignmentContext assignment, final SuggesterContext suggester) {
        if (call.data().returnType() != PrimitiveType.BOOLEAN) {
            return null;
        }
        final String methodName = call.data().name();

        final @Nullable String prefix = tryMatchPrefix(methodName, BOOL_METHOD_PREFIXES);
        if (prefix == null) {
            return null;
        }

        if (Character.isUpperCase(methodName.charAt(prefix.length()))) {
            return methodName;
        } else {
            return null;
        }
    }
}
