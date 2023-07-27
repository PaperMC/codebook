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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.ClassDataProvider;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.MethodDescriptor;
import java.io.IOException;
import java.util.HashMap;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mock;
import org.mockito.MockSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.MethodInsnNode;

@ExtendWith(MockitoExtension.class)
class LvtAssignmentSuggesterTest {

    private static final String RANDOM_SOURCE_NAME = "net/minecraft/util/RandomSource";

    private static final MockSettings LENIENT = withSettings().strictness(Strictness.LENIENT);
    private LvtAssignmentSuggester suggester;

    @Mock(strictness = Mock.Strictness.LENIENT)
    private ClassData randomSourceClass;

    @BeforeEach
    void setup() throws Exception {
        final ClassDataProvider provider = mock(LENIENT);
        final HypoContext context =
                HypoContext.builder().withContextProviders(provider).build();

        when(provider.findClass(RANDOM_SOURCE_NAME)).thenReturn(this.randomSourceClass);

        when(this.randomSourceClass.name()).thenReturn(RANDOM_SOURCE_NAME);

        this.suggester = new LvtAssignmentSuggester(context, new HashMap<>());
    }

    @ParameterizedTest
    @CsvFileSource(resources = "/io/papermc/codebook/lvt/LvtAssignmentSuggesterTest.csv", numLinesToSkip = 1)
    void testSuggester(
            final String methodName, final String methodOwner, final String methodDescriptor, final String expectedName)
            throws IOException {

        final AsmClassData owner = mock(LENIENT);
        final AsmMethodData method = mock(LENIENT);

        when(owner.kind()).thenReturn(ClassKind.CLASS);
        when(owner.name()).thenReturn(methodOwner);
        when(method.name()).thenReturn(methodName);

        final MethodDescriptor desc = MethodDescriptor.parseDescriptor(methodDescriptor);
        when(method.descriptor()).thenReturn(desc);

        // params() and returnType() methods will defer to the descriptor
        when(method.params()).thenCallRealMethod();
        when(method.param(anyInt())).thenCallRealMethod();
        when(method.returnType()).thenCallRealMethod();

        if (methodOwner.equals(RANDOM_SOURCE_NAME)) {
            when(owner.doesExtendOrImplement(this.randomSourceClass)).thenReturn(true);
        } else {
            when(owner.doesExtendOrImplement(this.randomSourceClass)).thenReturn(false);
        }

        final MethodInsnNode insn =
                new MethodInsnNode(Opcodes.INVOKEVIRTUAL, methodOwner, methodName, methodDescriptor);
        final @Nullable String result = this.suggester.suggestNameFromAssignment(owner, method, insn);

        assertEquals(expectedName, result);
    }
}
