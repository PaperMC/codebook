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

import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.hydrate.generic.LambdaClosure;
import dev.denwav.hypo.hydrate.generic.LocalClassClosure;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.MethodData;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntUnaryOperator;
import java.util.regex.Pattern;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.checkerframework.checker.nullness.qual.Nullable;

public class MissingMethodParam implements Report {

    private final Map<ClassData, List<String>> data = new ConcurrentHashMap<>();

    private void checkMappings(
            final MethodData method,
            final @Nullable MethodMapping methodMapping,
            final int descriptorParamOffset,
            final IntUnaryOperator descriptorToMappingOffset) {
        this.checkMappings(method, methodMapping, descriptorParamOffset, descriptorToMappingOffset, null);
    }

    private void checkMappings(
            final MethodData method,
            final @Nullable MethodMapping methodMapping,
            final int descriptorParamOffset,
            final IntUnaryOperator descriptorToMappingOffset,
            final @Nullable LambdaClosure lambdaClosure) {
        if (method.params().size() == descriptorParamOffset) {
            return;
        }
        if (methodMapping == null
                || (method.params().size() - descriptorParamOffset
                        > methodMapping.getParameterMappings().size())) {
            // != should have been sufficient here, but hypo's CopyMappingsDown for constructors incorrectly applies
            // mappings to implicit constructor params
            this.reportMissingParam(
                    method, methodMapping, descriptorParamOffset, descriptorToMappingOffset, lambdaClosure);
        }
    }

    private static final Pattern ANONYMOUS_CLASS = Pattern.compile(".+\\$\\d+$");

    private static boolean shouldSkipMapping(
            final MethodData method,
            final ClassData parentClass,
            final @Nullable ClassData superClass,
            final @Nullable List<LambdaClosure> lambdaCalls) {
        final String name = method.name();
        if (name.startsWith("access$") && method.isSynthetic()) {
            // never in source
            return true;
        } else if (name.startsWith("lambda$")
                && method.isSynthetic()
                && (lambdaCalls == null || lambdaCalls.isEmpty())) {
            // lambdas that had their use stripped by mojang
            return true;
        } else {
            final String descriptorText = method.descriptorText();
            if (superClass != null
                    && superClass.name().equals("java/lang/Enum")
                    && name.equals("valueOf")
                    && descriptorText.startsWith("(Ljava/lang/String;)")) {
                // created by the compiler
                return true;
            } else if (parentClass.is(ClassKind.RECORD)
                    && name.equals("equals")
                    && descriptorText.equals("(Ljava/lang/Object;)Z")) {
                // created by the compiler
                return true;
            } else if (method.isSynthetic() && method.get(HypoHydration.SYNTHETIC_TARGET) != null) {
                // don't trust isBridge, apparently it's not always accurate
                return true;
            } else {
                return false;
            }
        }
    }

    private void handleConstructorMappings(
            final MethodData method,
            final ClassData parentClass,
            final @Nullable MethodMapping methodMapping,
            final @Nullable LocalClassClosure localClassClosure)
            throws IOException {
        if (parentClass.is(ClassKind.ENUM)) {
            // enum constructors include name and ordinal
            this.checkMappings(method, methodMapping, 2, i -> i + 1);
        } else {
            if (!ANONYMOUS_CLASS.matcher(parentClass.name()).matches()) {
                // anonymous classes cannot have constructors in source
                if (parentClass.outerClass() != null) {
                    final int descriptorParamOffset = parentClass.isStaticInnerClass() ? 0 : 1;
                    if (localClassClosure == null) {
                        this.checkMappings(method, methodMapping, descriptorParamOffset, i -> i + 1);
                    } else {
                        this.checkMappings(
                                method,
                                methodMapping,
                                descriptorParamOffset + localClassClosure.getParamLvtIndices().length,
                                i -> i + 1);
                    }
                } else {
                    this.checkMappings(method, methodMapping, 0, i -> i + 1);
                }
            }
        }
    }

    public void handleCheckingMappings(
            final MethodData method,
            final ClassData parentClass,
            final @Nullable ClassData superClass,
            final @Nullable List<LambdaClosure> lambdaCalls,
            final @Nullable MethodMapping methodMapping,
            final int @Nullable [] outerMethodParamLvtIndices,
            final @Nullable LambdaClosure lambdaClosure,
            final @Nullable LocalClassClosure localClassClosure)
            throws IOException {
        if (shouldSkipMapping(method, parentClass, superClass, lambdaCalls)) {
            return;
        }
        if (method.isConstructor()) {
            this.handleConstructorMappings(method, parentClass, methodMapping, localClassClosure);
        } else {
            if (outerMethodParamLvtIndices == null) {
                this.checkMappings(method, methodMapping, 0, i -> i + (method.isStatic() ? 0 : 1));
            } else {
                final int descriptorOffset;
                if (!method.isStatic() && outerMethodParamLvtIndices.length > 0 && outerMethodParamLvtIndices[0] == 0) {
                    descriptorOffset = outerMethodParamLvtIndices.length - 1;
                } else {
                    descriptorOffset = outerMethodParamLvtIndices.length;
                }
                this.checkMappings(
                        method, methodMapping, descriptorOffset, i -> i + (method.isStatic() ? 0 : 1), lambdaClosure);
            }
        }
    }

    private void reportMissingParam(
            final MethodData method,
            final @Nullable MethodMapping methodMapping,
            final int descriptorParamOffset,
            final IntUnaryOperator descriptorToMappingOffset,
            final @Nullable LambdaClosure lambdaClosure) {
        final ClassData parentClass = method.parentClass();
        final StringBuilder msg = new StringBuilder("\t#%s %s".formatted(method.name(), method.descriptorText()));
        if (lambdaClosure != null) {
            final MethodData containingMethod = lambdaClosure.getContainingMethod();
            msg.append("%n\t\tLambda Source: %s#%s %s"
                    .formatted(
                            containingMethod.parentClass().equals(parentClass)
                                    ? ""
                                    : containingMethod.parentClass().name(),
                            containingMethod.name(),
                            containingMethod.descriptorText()));
        }
        for (int i = descriptorParamOffset; i < method.params().size(); i++) {
            final int paramIdx = i;
            final int lastIdxOfDot = method.param(i).toString().lastIndexOf('.');
            msg.append("%n\t\t%s\t%-50s\t%s"
                    .formatted(
                            i,
                            method.param(i).toString().substring(lastIdxOfDot + 1),
                            Optional.ofNullable(methodMapping)
                                    .flatMap(m -> m.getParameterMapping(descriptorToMappingOffset.applyAsInt(paramIdx)))
                                    .map(Mapping::getDeobfuscatedName)
                                    .orElse("<<MISSING>>")));
        }
        this.data.computeIfAbsent(parentClass, ignored -> new ArrayList<>()).add(msg.toString());
    }

    @Override
    public String generate() {
        final StringBuilder output = new StringBuilder();
        this.data.entrySet().stream()
                .sorted(Comparator.comparing(entry -> entry.getKey().name()))
                .forEach(entry -> {
                    output.append("Missing param mappings in %s, Method Count: %s, Param Count: TODO\n"
                            .formatted(entry.getKey().name(), entry.getValue().size()));
                    entry.getValue().forEach(msg -> output.append(msg).append("\n"));
                });
        return output.toString();
    }
}
