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

import static java.util.Objects.requireNonNullElse;

import com.google.common.collect.Iterables;
import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmConstructorData;
import dev.denwav.hypo.asm.AsmFieldData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.Visibility;
import io.papermc.codebook.exceptions.UnexpectedException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class FixJarPage extends CodeBookPage {

    private final HypoContext context;

    @Inject
    public FixJarPage(@Hypo final HypoContext context) {
        this.context = context;
    }

    @Override
    public void exec() {
        try {
            this.fixJar();
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to fix jar", e);
        }
    }

    private void fixJar() throws IOException {
        final var tasks = new ArrayList<Future<?>>();
        for (final ClassData classData : this.context.getProvider().allClasses()) {
            final var task = this.context.getExecutor().submit(() -> {
                this.processClass((AsmClassData) classData);
            });
            tasks.add(task);
        }

        try {
            for (final Future<?> task : tasks) {
                task.get();
            }
        } catch (final ExecutionException e) {
            throw new UnexpectedException("Failed to fix jar", e.getCause());
        } catch (final InterruptedException e) {
            throw new UnexpectedException("Jar fixing interrupted", e);
        }
    }

    private void processClass(final AsmClassData classData) {
        OverrideAnnotationAdder.addAnnotations(classData);
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

                final @Nullable MethodData targetMethod = method.get(HypoHydration.SYNTHETIC_SOURCE);
                final MethodData baseMethod = requireNonNullElse(targetMethod, method);

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
                final MethodNode node;
                if (method instanceof final AsmMethodData asm) {
                    node = asm.getNode();
                } else if (method instanceof final AsmConstructorData asm) {
                    node = asm.getNode();
                } else {
                    // should never happen
                    throw new IllegalStateException("Unknown type: " + method.getClass());
                }

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

    private static final class RecordFieldAccessFixer {
        private static final int RESET_ACCESS = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);

        private RecordFieldAccessFixer() {}

        private static void fixClass(final AsmClassData classData) {
            if (classData.kind() != ClassKind.RECORD) {
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
