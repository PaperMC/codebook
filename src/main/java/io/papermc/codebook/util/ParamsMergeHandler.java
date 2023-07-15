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

package io.papermc.codebook.util;

import io.papermc.codebook.exceptions.UnexpectedException;
import java.util.Objects;
import java.util.stream.Stream;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.merge.MappingSetMergerHandler;
import org.cadixdev.lorenz.merge.MergeContext;
import org.cadixdev.lorenz.merge.MergeResult;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.InnerClassMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.cadixdev.lorenz.model.MethodParameterMapping;
import org.cadixdev.lorenz.model.TopLevelClassMapping;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class ParamsMergeHandler implements MappingSetMergerHandler {

    @Override
    public MergeResult<TopLevelClassMapping> mergeTopLevelClassMappings(
            final TopLevelClassMapping left,
            final TopLevelClassMapping right,
            final MappingSet target,
            final MergeContext context) {
        throw new UnexpectedException("Unexpectedly merged class: %s".formatted(left.getFullObfuscatedName()));
    }

    @Override
    public MergeResult<TopLevelClassMapping> mergeDuplicateTopLevelClassMappings(
            final TopLevelClassMapping left,
            final TopLevelClassMapping right,
            final @Nullable TopLevelClassMapping rightContinuation,
            final MappingSet target,
            final MergeContext context) {
        return new MergeResult<>(
                target.createTopLevelClassMapping(left.getObfuscatedName(), left.getDeobfuscatedName()), right);
    }

    @Override
    public MergeResult<InnerClassMapping> mergeInnerClassMappings(
            final InnerClassMapping left,
            final InnerClassMapping right,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        throw new UnexpectedException("Unexpectedly merged class: %s".formatted(left.getFullObfuscatedName()));
    }

    @Override
    public MergeResult<InnerClassMapping> mergeDuplicateInnerClassMappings(
            final InnerClassMapping left,
            final InnerClassMapping right,
            final @Nullable InnerClassMapping rightContinuation,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        return new MergeResult<>(
                target.createInnerClassMapping(left.getObfuscatedName(), left.getDeobfuscatedName()), right);
    }

    @Override
    public FieldMapping mergeFieldMappings(
            final FieldMapping left,
            final @Nullable FieldMapping strictRight,
            final @Nullable FieldMapping looseRight,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        throw new UnexpectedException("Unexpectedly merged field: %s".formatted(left.getFullObfuscatedName()));
    }

    @Override
    public FieldMapping mergeDuplicateFieldMappings(
            final FieldMapping left,
            final @Nullable FieldMapping strictRightDuplicate,
            final @Nullable FieldMapping looseRightDuplicate,
            final @Nullable FieldMapping strictRightContinuation,
            final @Nullable FieldMapping looseRightContinuation,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        return target.createFieldMapping(left.getSignature(), left.getDeobfuscatedName());
    }

    @Override
    public FieldMapping addLeftFieldMapping(
            final FieldMapping left, final ClassMapping<?, ?> target, final MergeContext context) {
        return target.createFieldMapping(left.getSignature(), left.getDeobfuscatedName());
    }

    @Override
    public MergeResult<MethodMapping> mergeMethodMappings(
            final MethodMapping left,
            final @Nullable MethodMapping standardRight,
            final @Nullable MethodMapping wiggledRight,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        throw new UnexpectedException("Unexpectedly merged method: %s".formatted(left.getFullObfuscatedName()));
    }

    @Override
    public MergeResult<MethodMapping> mergeDuplicateMethodMappings(
            final MethodMapping left,
            final @Nullable MethodMapping standardRightDuplicate,
            final @Nullable MethodMapping wiggledRightDuplicate,
            final @Nullable MethodMapping standardRightContinuation,
            final @Nullable MethodMapping wiggledRightContinuation,
            final ClassMapping<?, ?> target,
            final MergeContext context) {
        return new MergeResult<>(
                target.createMethodMapping(left.getSignature(), left.getDeobfuscatedName()),
                Stream.of(standardRightDuplicate, wiggledRightDuplicate)
                        .filter(Objects::nonNull)
                        .toList());
    }

    @Override
    public MethodParameterMapping mergeParameterMappings(
            final MethodParameterMapping left,
            final MethodParameterMapping right,
            final MethodMapping target,
            final MergeContext context) {
        throw new UnexpectedException("Unexpectedly merged method: %s".formatted(left.getFullObfuscatedName()));
    }

    // Don't take anything from yarn
    @Override
    public MergeResult<@Nullable TopLevelClassMapping> addRightTopLevelClassMapping(
            final TopLevelClassMapping right, final MappingSet target, final MergeContext context) {
        return emptyMergeResult();
    }

    @Override
    public MergeResult<@Nullable InnerClassMapping> addRightInnerClassMapping(
            final InnerClassMapping right, final ClassMapping<?, ?> target, final MergeContext context) {
        return emptyMergeResult();
    }

    @Override
    public @Nullable FieldMapping addRightFieldMapping(
            final FieldMapping right, final ClassMapping<?, ?> target, final MergeContext context) {
        return null;
    }

    @Override
    public MergeResult<@Nullable MethodMapping> addRightMethodMapping(
            final MethodMapping right, final ClassMapping<?, ?> target, final MergeContext context) {
        return emptyMergeResult();
    }

    private static final MergeResult<?> emptyMergeResult = new MergeResult<>(null);

    private static <T> MergeResult<@Nullable T> emptyMergeResult() {
        @SuppressWarnings("unchecked")
        final MergeResult<T> res = (MergeResult<T>) emptyMergeResult;
        return res;
    }
}
