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

package io.papermc.codebook.report.type;

import dev.denwav.hypo.model.data.MethodData;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.objectweb.asm.tree.MethodInsnNode;

public class MissingMethodLvtSuggestion implements Report {

    private static final Comparator<Map.Entry<String, AtomicInteger>> COMPARATOR =
            Comparator.comparing(e -> e.getValue().get());

    private final Map<String, AtomicInteger> data = new ConcurrentHashMap<>();

    public void reportMissingMethodLvtSuggestion(final MethodData method, final MethodInsnNode insn) {
        this.data
                .computeIfAbsent(method.name() + "," + insn.owner + "," + insn.desc, (k) -> new AtomicInteger(0))
                .incrementAndGet();
    }

    @Override
    public String generate() {
        final StringBuilder output = new StringBuilder();
        this.data.entrySet().stream()
                .sorted(COMPARATOR.reversed())
                .forEach(s -> output.append("missed: %s -- %s times%n".formatted(s.getKey(), s.getValue())));
        return output.toString();
    }
}
