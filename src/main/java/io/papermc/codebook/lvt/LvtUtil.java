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

import dev.denwav.hypo.asm.HypoAsmUtil;
import dev.denwav.hypo.model.data.types.JvmType;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Type;

public final class LvtUtil {

    private LvtUtil() {}

    public static JvmType toJvmType(final String desc) {
        return HypoAsmUtil.toJvmType(Type.getType(desc));
    }

    public static String capitalize(final String name, final int index) {
        return Character.toUpperCase(name.charAt(index)) + name.substring(index + 1);
    }

    public static @Nullable String decapitalize(final String name, final int index) {
        if (!Character.isUpperCase(name.charAt(index))) {
            // If the char isn't uppercase, that means it isn't following the typical `lowerCamelCase`
            // Java method naming scheme how we expect, so we can't be sure it means what we think it
            // means in this instance
            return null;
        } else {
            return Character.toLowerCase(name.charAt(index)) + name.substring(index + 1);
        }
    }
}
