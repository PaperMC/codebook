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
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
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
import org.objectweb.asm.tree.MethodNode;

public final class FixJarPage extends AsmProcessorPage {

    @Inject
    public FixJarPage(@Hypo final HypoContext context) {
        super(context);
    }

    @Override
    protected void processClass(final AsmClassData classData) throws IOException {
        OverrideAnnotationAdder.addAnnotations(classData);
        DeprecatedAnnotationAdder.addAnnotations(classData);
    }

    private static final class OverrideAnnotationAdder {
        private OverrideAnnotationAdder() {}

        private static void addAnnotations(final AsmClassData classData) throws IOException {
            for (final MethodData method : classData.methods()) {
                // Synthetic methods have no declaration in source, so @Override doesn't apply to them
                if (method.isStatic() || method.visibility() == Visibility.PRIVATE || method.isSynthetic()) {
                    continue;
                }
                if (isInitializer(method)) {
                    continue;
                }

                if (overrides(method)) {
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

        private static boolean overrides(final MethodData method) throws IOException {
            if (hasSuperMethod(method)) {
                return true;
            }

            // Overrides with a different descriptor are only linked to the super method through a bridge in this class.
            // A bridge in another class means that class inherits this method to implement something, which doesn't
            // make this method an override.
            final @Nullable Set<MethodData> syntheticSources = method.get(HypoHydration.SYNTHETIC_SOURCES);
            if (syntheticSources != null) {
                for (final MethodData source : syntheticSources) {
                    if (source.parentClass().equals(method.parentClass()) && hasSuperMethod(source)) {
                        return true;
                    }
                }
            }
            return false;
        }

        private static boolean hasSuperMethod(final MethodData method) throws IOException {
            final @Nullable MethodData superMethod = method.superMethod();
            if (superMethod == null) {
                return false;
            }

            // Hypo treats Object as the super class of interfaces, but interfaces only have its public methods as
            // members, so @Override isn't allowed for just clone() or finalize() (JLS 9.2, 9.6.4.4). superMethod() only
            // gives a single method though, so the method could also be declared by a superinterface.
            final ClassData parentClass = method.parentClass();
            if (parentClass.is(ClassKind.INTERFACE) && superMethod.visibility() != Visibility.PUBLIC) {
                return isDeclaredBySuperInterface(method, parentClass);
            }
            return true;
        }

        private static boolean isDeclaredBySuperInterface(final MethodData method, final ClassData classData)
                throws IOException {
            for (final ClassData superInterface : classData.interfaces()) {
                final @Nullable MethodData superMethod = superInterface.method(method.name(), method.descriptor());
                if (superMethod != null && method.overrides(superMethod)) {
                    return true;
                }
                if (isDeclaredBySuperInterface(method, superInterface)) {
                    return true;
                }
            }
            return false;
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

    private static <T> List<T> appendToList(final @Nullable List<T> list, final T value) {
        if (list != null) {
            list.add(value);
            return list;
        } else {
            return new ArrayList<>(List.of(value));
        }
    }
}
