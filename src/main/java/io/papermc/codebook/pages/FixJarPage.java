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

package io.papermc.codebook.pages;

import com.google.common.collect.Iterables;
import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmFieldData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.Visibility;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class FixJarPage extends AsmProcessorPage {

    @Inject
    public FixJarPage(@Hypo final HypoContext context) {
        super(context);
    }

    @Override
    protected void processClass(final AsmClassData classData) throws IOException {
        OverrideAnnotationAdder.addAnnotations(classData);
        EmptyRecordFixer.fixClass(classData);
        RecordFieldAccessFixer.fixClass(classData);
        DeprecatedAnnotationAdder.addAnnotations(classData);
    }

    private static final class OverrideAnnotationAdder {
        private OverrideAnnotationAdder() {}

        private static void addAnnotations(final AsmClassData classData) {
            for (final MethodData method : classData.methods()) {
                if (method.isStatic() || method.visibility() == Visibility.PRIVATE) {
                    continue;
                }
                if (isInitializer(method)) {
                    continue;
                }

                final MethodData baseMethod;
                final @Nullable Set<MethodData> syntheticSources = method.get(HypoHydration.SYNTHETIC_SOURCES);
                if (syntheticSources == null) {
                    baseMethod = method;
                } else {
                    outer:
                    {
                        for (final MethodData targetMethod : syntheticSources) {
                            if (targetMethod.parentClass().equals(method.parentClass())) {
                                baseMethod = targetMethod;
                                break outer;
                            }
                        }
                        return;
                    }
                }

                if (baseMethod.superMethod() != null) {
                    final MethodNode node = ((AsmMethodData) method).getNode();
                    final var annoClass = "Ljava/lang/Override;";
                    if (node.invisibleAnnotations == null
                            || !Iterables.any(node.invisibleAnnotations, a -> a.desc.equals(annoClass))) {
                        node.invisibleAnnotations =
                                appendToList(node.invisibleAnnotations, new AnnotationNode(annoClass));
                    }
                }
            }
        }

        private static boolean isInitializer(final MethodData method) {
            return method.name().equals("<init>") || method.name().equals("<clinit>");
        }
    }

    private static final class DeprecatedAnnotationAdder {
        private DeprecatedAnnotationAdder() {}

        private static void addAnnotations(final AsmClassData classData) {
            for (final MethodData method : classData.methods()) {
                final MethodNode node = ((AsmMethodData) method).getNode();

                if ((node.access & Opcodes.ACC_DEPRECATED) != 0) {
                    final var annoClass = "Ljava/lang/Deprecated;";
                    if (node.visibleAnnotations == null
                            || !Iterables.any(node.visibleAnnotations, a -> a.desc.equals(annoClass))) {
                        node.visibleAnnotations = appendToList(node.visibleAnnotations, new AnnotationNode(annoClass));
                    }
                }
            }
        }
    }

    private static final class EmptyRecordFixer {

        private EmptyRecordFixer() {}

        private static void fixClass(final AsmClassData classData) throws IOException {
            if (classData.is(ClassKind.RECORD)) {
                return;
            }

            final @Nullable ClassData superClass = classData.superClass();
            if (superClass == null) {
                return;
            }

            if (superClass.name().equals("java/lang/Record")) {
                // extends record, but is not marked as such
                classData.getNode().access |= Opcodes.ACC_RECORD;
            }
        }
    }

    private static final class RecordFieldAccessFixer {
        private static final int RESET_ACCESS = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);

        private RecordFieldAccessFixer() {}

        private static void fixClass(final AsmClassData classData) {
            if (classData.isNot(ClassKind.RECORD)) {
                return;
            }

            for (final FieldData field : classData.fields()) {
                if (!field.isStatic() && field.visibility() != Visibility.PRIVATE && field.isFinal()) {
                    final FieldNode node = ((AsmFieldData) field).getNode();
                    node.access = (node.access & RESET_ACCESS) | Opcodes.ACC_PRIVATE;
                }
            }
        }
    }

    private static <T> List<T> appendToList(final @Nullable List<T> list, final T value) {
        if (list != null) {
            list.add(value);
            return list;
        } else {
            return new ArrayList<>(List.of(value));
        }
    }
}
