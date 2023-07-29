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

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmConstructorData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.hydrate.generic.MethodClosure;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.HypoKey;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.types.JvmType;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.parchmentmc.feather.mapping.MappingDataContainer;

public class LvtNamer {

    public static final HypoKey<Set<String>> SCOPED_NAMES = HypoKey.create("Scoped Names");

    private final MappingDataContainer mappings;
    private final LvtSuggester lvtSuggester;
    public final Map<String, AtomicInteger> missedNameSuggestions = new ConcurrentHashMap<>();

    public LvtNamer(final HypoContext context, final MappingDataContainer mappings) throws IOException {
        this.mappings = mappings;
        this.lvtSuggester = new LvtSuggester(context, this.missedNameSuggestions);
    }

    public void processClass(final AsmClassData classData) throws IOException {
        for (final MethodData method : classData.methods()) {
            this.fillNames(method);
        }
    }

    public void fillNames(final MethodData method) throws IOException {
        final @Nullable Set<String> names = method.get(SCOPED_NAMES);
        if (names != null) {
            // If scoped names is already filled out, this method has already been visited
            return;
        }

        // Determine if this method exists as a lambda expression inside another method
        // If it does, we need to keep track of the LVTs we inherit
        @Nullable AsmMethodData outerMethod = null;
        int @Nullable [] outerMethodParamLvtIndices = null;
        final @Nullable List<MethodClosure<MethodData>> lambdaCalls = method.get(HypoHydration.LAMBDA_CALLS);
        // Only track synthetic, non-synthetic means a method reference which does not behave as a closure (does not
        // capture LVT)
        if (lambdaCalls != null && method.isSynthetic()) {
            for (final MethodClosure<MethodData> lambdaCall : lambdaCalls) {
                // lambdaCall.getClosure() -> The lambda method
                // lambdaCall.getContainingMethod() -> The outer method
                if (lambdaCall.getClosure().equals(method)) {
                    outerMethod = (AsmMethodData) lambdaCall.getContainingMethod();
                    if (outerMethod.equals(method)) {
                        // lambdas can be recursive
                        outerMethod = null;
                        continue;
                    }
                    outerMethodParamLvtIndices = lambdaCall.getParamLvtIndices();
                    // there can only be 1 outer method
                    break;
                }
            }
        }

        final MethodNode node;
        if (method instanceof AsmMethodData) {
            node = ((AsmMethodData) method).getNode();
        } else {
            node = ((AsmConstructorData) method).getNode();
        }

        final ClassData parentClass = method.parentClass();
        // A method cannot be both a lambda expression and a local class, so if we've already determined an outer
        // method,
        // there's nothing to do here.
        localCheck:
        if (outerMethod == null) {
            final @Nullable List<MethodClosure<ClassData>> localClasses = parentClass.get(HypoHydration.LOCAL_CLASSES);
            if (localClasses == null || localClasses.isEmpty()) {
                break localCheck;
            }

            final MethodClosure<ClassData> localClass = localClasses.get(0);
            outerMethod = (AsmMethodData) localClass.getContainingMethod();
            // local classes don't capture as lvt, so don't assign `outerMethodParamLvtIndices`
        }

        // This method (`fillNames`) will ensure the outer method has names defined in its scope first.
        // If the scope is already computed this is a no-op
        if (outerMethod != null) {
            this.fillNames(outerMethod);
        }

        final MappingDataContainer.@Nullable MethodData methodMapping;
        final MappingDataContainer.@Nullable ClassData classMapping = this.mappings.getClass(parentClass.name());
        if (classMapping == null) {
            methodMapping = null;
        } else {
            methodMapping = classMapping.getMethod(method.name(), method.descriptorText());
        }

        // We inherit names from our outer scope, if it exists. These names will be included in our scope for any
        // potential inner scopes (other nested lambdas or local classes) that are present in this method too
        final Set<String> scopedNames;
        if (outerMethod != null) {
            final @Nullable Set<String> outerScope = outerMethod.get(SCOPED_NAMES);
            scopedNames = new HashSet<>(outerScope == null ? Set.of() : outerScope);
        } else {
            scopedNames = new HashSet<>();
        }

        // If there's no LVT table there's nothing for us to process
        if (node.localVariables == null) {
            method.store(SCOPED_NAMES, scopedNames);
            return;
        }

        // remember which of our LVTs we've already set from captured values
        // just so we don't overwrite these later
        final int[] ourCapturedLvts =
                new int[outerMethodParamLvtIndices == null ? 0 : outerMethodParamLvtIndices.length];
        Arrays.fill(ourCapturedLvts, -1);
        // -1 if nothing
        int ourCapturedLvtIndex = 0;

        // set our captured lvt names, if possible
        // only applies to lambda methods, not local classes
        if (outerMethodParamLvtIndices != null) {
            // param counts are typically low (< 20), so not doing anything clever here
            final List<LocalVariableNode> outerLvts = outerMethod.getNode().localVariables;
            for (final LocalVariableNode outerLvt : outerLvts) {
                final int ourLvtIndex = find(outerMethodParamLvtIndices, outerLvt.index);
                if (ourLvtIndex != -1 && find(ourCapturedLvts, ourLvtIndex) == -1) {
                    ourCapturedLvts[ourCapturedLvtIndex++] = ourLvtIndex;
                    for (final LocalVariableNode ourLvt : node.localVariables) {
                        // we can apply this name to any matching LVT slot, since duplicates
                        // are guaranteed to be in different scopes
                        if (ourLvt.index == ourLvtIndex && ourLvt.desc.equals(outerLvt.desc)) {
                            ourLvt.name = outerLvt.name;
                        }
                    }

                    final int ourLvtParamIndex = fromLvtToParamIndex(ourLvtIndex, method);
                    // Also update the parameters table if this LVT slot is a parameter
                    if (ourLvtParamIndex != -1
                            && node.parameters != null
                            && node.parameters.size() > ourLvtParamIndex) {
                        node.parameters.get(ourLvtParamIndex).name = outerLvt.name;
                    }
                }
            }
        }

        // Keep track of which names (and their slots) we've used
        // This means if we come across a slot again with the same type, we can
        // re-use the existing name.
        //
        // This is ideal because sometimes the different scopes are actually for the same variable
        // by merging these cases we can prevent names from appearing in code with de-dupe numbers
        // when there are actually no duplicates.
        //
        // Examples:
        //     Thing thing;
        //     if (flag) {
        //         thing = createThing();
        //     } else {
        //         thing = null;
        //     }
        //
        // This will (probably) result in 2 separate scopes, and the same variable will have 2 separate
        // LVT entries for the same slot. By re-using the name we can prevent this from being created
        // as `thing1` or `thing2` for no reason.
        int usedNameIndex = 0;
        final @Nullable UsedLvtName[] usedNames = new UsedLvtName[node.localVariables.size()];

        outer:
        for (final LocalVariableNode lvt : node.localVariables) {
            if (lvt.index == 0 && !method.isStatic()) {
                if (!"this".equals(lvt.name)) {
                    lvt.name = "this";
                }
                continue;
            }

            if (ourCapturedLvtIndex != -1) {
                // Check if we've already set this name, if so, skip it
                if (find(ourCapturedLvts, lvt.index) != -1) {
                    continue;
                }
            }

            for (int i = 0; i < usedNameIndex; i++) {
                @Nullable final UsedLvtName used = usedNames[i];
                if (used != null && used.index == lvt.index && used.desc.equals(lvt.desc)) {
                    lvt.name = used.name;
                    continue outer;
                }
            }

            @Nullable String mappedName = null;
            if (methodMapping != null) {
                final MappingDataContainer.@Nullable ParameterData paramMapping =
                        methodMapping.getParameter((byte) lvt.index);
                if (paramMapping != null) {
                    final @Nullable String paramName = paramMapping.getName();
                    if (paramName != null) {
                        mappedName = LvtSuggester.determineFinalName(paramName, scopedNames);
                    }
                }
            }

            final String selectedName;
            if (mappedName != null) {
                selectedName = mappedName;
            } else {
                selectedName = this.lvtSuggester.suggestName(node, lvt, scopedNames);
            }

            lvt.name = selectedName;
            usedNames[usedNameIndex++] = new UsedLvtName(lvt.name, lvt.desc, lvt.index);

            // Also update the parameters table if this LVT slot is a parameter
            final int paramIndexFromLvt = fromLvtToParamIndex(lvt.index, method);
            if (paramIndexFromLvt != -1 && node.parameters != null && node.parameters.size() > paramIndexFromLvt) {
                node.parameters.get(paramIndexFromLvt).name = selectedName;
            }
        }

        method.store(SCOPED_NAMES, scopedNames);
    }

    private record UsedLvtName(String name, String desc, int index) {}

    private static int find(final int[] array, final int value) {
        return find(array, value, array.length);
    }

    private static int find(final int[] array, final int value, final int len) {
        for (int i = 0; i < len; i++) {
            if (array[i] == value) {
                return i;
            }
        }
        return -1;
    }

    /*
     * Transform the given LVT index into a parameter index.
     */
    private static int fromLvtToParamIndex(final int lvtIndex, final MethodData method) {
        int currentIndex = 0;
        int currentLvtIndex = method.isStatic() ? 0 : 1;

        for (final JvmType param : method.params()) {
            if (currentLvtIndex == lvtIndex) {
                return currentIndex;
            }

            currentIndex++;
            currentLvtIndex++;
            if (param == PrimitiveType.LONG || param == PrimitiveType.DOUBLE) {
                currentLvtIndex++;
            }
        }

        return -1;
    }
}
