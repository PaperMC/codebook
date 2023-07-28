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

import dev.denwav.hypo.core.HypoContext;
import io.papermc.codebook.lvt.suggestion.context.LvtContext.Field;
import io.papermc.codebook.lvt.suggestion.context.LvtContext.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class RootLvtSuggester extends LvtSuggester {

    private static final List<Function<HypoContext, LvtSuggester>> SUGGESTERS = List.of(GenericSuggester::new);

    public final Map<String, AtomicInteger> missedNameSuggestions;
    private final List<LvtSuggester> delegates;

    public RootLvtSuggester(final HypoContext hypoContext, final Map<String, AtomicInteger> missedNameSuggestions) {
        super(hypoContext);
        this.missedNameSuggestions = missedNameSuggestions;
        this.delegates =
                SUGGESTERS.stream().map(func -> func.apply(hypoContext)).toList();
    }

    @Override
    public @Nullable String suggestFromMethod(final Method ctx) {
        @Nullable String suggestion;
        for (final LvtSuggester delegate : this.delegates) {
            suggestion = delegate.suggestFromMethod(ctx);
            if (suggestion != null) {
                return suggestion;
            }
        }
        this.missedNameSuggestions
                .computeIfAbsent(
                        ctx.data().name() + "," + ctx.insn().owner + "," + ctx.insn().desc, (k) -> new AtomicInteger(0))
                .incrementAndGet();
        return null;
    }

    @Override
    public @Nullable String suggestFromField(final Field ctx) {
        @Nullable String suggestion;
        for (final LvtSuggester delegate : this.delegates) {
            suggestion = delegate.suggestFromField(ctx);
            if (suggestion != null) {
                return suggestion;
            }
        }
        return null;
    }
}
