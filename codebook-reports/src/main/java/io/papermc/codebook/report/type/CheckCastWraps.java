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

import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.MethodInsnNode;

public class CheckCastWraps implements Report {

    private final Map<CacheKey, Integer> cache = new ConcurrentHashMap<>();

    @Override
    public String generate() {
        final StringBuilder sb = new StringBuilder();
        this.cache.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEachOrdered(entry -> {
                    sb.append(entry.getKey().className)
                            .append("#")
                            .append(entry.getKey().methodName)
                            .append(" ")
                            .append(entry.getKey().descriptor)
                            .append(" ")
                            .append(entry.getKey().itf)
                            .append(" ")
                            .append(entry.getValue())
                            .append("\n");
                });
        return sb.toString();
    }

    public void report(final MethodInsnNode insn) {
        final var key = new CacheKey(insn.owner, insn.name, insn.desc, insn.itf);
        this.cache.compute(key, (k, v) -> v == null ? 1 : v + 1);
    }

    private record CacheKey(String className, String methodName, String descriptor, boolean itf) {}
}