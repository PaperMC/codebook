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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.objectweb.asm.tree.MethodInsnNode;

class LvtAssignmentSuggesterTest {

    @ParameterizedTest
    @CsvFileSource(resources = "/io/papermc/codebook/lvt/LvtAssignmentSuggesterTest.csv", numLinesToSkip = 1)
    public void testSuggester(
            final String methodName,
            final String methodOwner,
            final String methodDescriptor,
            final String expectedName) throws IOException {
        final @Nullable String result = LvtAssignmentSuggester.suggestNameFromAssignment(
                null, methodName, new MethodInsnNode(-1, methodOwner, methodName, methodDescriptor));
        assertEquals(expectedName, result);
    }
}
