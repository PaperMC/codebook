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

import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

final class SuggestionUtil {

    private SuggestionUtil() {}

    static @Nullable String tryMatchPrefix(final String methodName, final List<String> possiblePrefixes) {
        // skip any exact match
        if (possiblePrefixes.contains(methodName)) {
            return null;
        }
        @Nullable String prefix = null;
        for (final String possiblePrefix : possiblePrefixes) {
            if (!possiblePrefix.equals(methodName) && methodName.startsWith(possiblePrefix)) {
                prefix = possiblePrefix;
                break;
            }
        }
        return prefix;
    }
}
