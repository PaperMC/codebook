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

import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.hydrate.generic.LambdaClosure;
import dev.denwav.hypo.hydrate.generic.LocalClassClosure;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.HypoKey;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.types.JvmType;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import io.papermc.codebook.report.ReportType;
import io.papermc.codebook.report.Reports;
import io.papermc.codebook.report.type.MissingMethodParam;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.model.Mapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.ParameterNode;

public class LvtNamer {

    public static final HypoKey<Set<String>> SCOPED_NAMES = HypoKey.create("Scoped Names");

    private final MappingSet mappings;
    private final LvtTypeSuggester lvtTypeSuggester;
    private final Reports reports;
    private final Injector reportsInjector;
    private final RootLvtSuggester lvtAssignSuggester;

    public LvtNamer(final HypoContext context, final MappingSet mappings, final Reports reports) throws IOException {
        this.mappings = mappings;
        this.lvtTypeSuggester = new LvtTypeSuggester(context);
        this.reports = reports;
        this.reportsInjector = Guice.createInjector(reports);
        this.lvtAssignSuggester = new RootLvtSuggester(context, this.lvtTypeSuggester, this.reportsInjector);
    }

    public void processClass(final AsmClassData classData) throws IOException {
        for (final MethodData method : classData.methods()) {
            this.fillNames(method);
        }
    }

    public void fillNames(final MethodData method) throws IOException {
        // Hopefully the overhead here won't be too bad. This method is generally safe in parallel, but
        // `fixOuterScopeName` does require
        // going back to modify previous methods, so we need to protect against multiple conflicting writes. Each class
        // is run in parallel,
        // so most of the time this shouldn't block between threads. Handling local classes is the only time we leave
        // our "lane" so-to-speak
        // and access another class from this method, which is what we are protecting against here.
        //noinspection SynchronizationOnLocalVariableOrMethodParameter
        synchronized (method) {
            this.fillNames0(method);
        }
    }

    private void fillNames0(final MethodData method) throws IOException {
        final @Nullable Set<String> names = method.get(SCOPED_NAMES);
        if (names != null) {
            // If scoped names is already filled out, this method has already been visited
            return;
        }

        // Determine if this method exists as a lambda expression inside another method
        // If it does, we need to keep track of the LVTs we inherit
        @Nullable AsmMethodData outerMethod = null;
        int @Nullable [] outerMethodParamLvtIndices = null;
        @Nullable LambdaClosure lambdaClosure = null;
        final @Nullable List<LambdaClosure> lambdaCalls = method.get(HypoHydration.LAMBDA_CALLS);
        // Only track synthetic, non-synthetic means a method reference which does not behave as a closure (does not
        // capture LVT)
        if (lambdaCalls != null && method.isSynthetic()) {
            for (final LambdaClosure lambdaCall : lambdaCalls) {
                // lambdaCall.getClosure() -> The lambda method
                // lambdaCall.getContainingMethod() -> The outer method
                if (lambdaCall.getLambda().equals(method)) {
                    outerMethod = (AsmMethodData) lambdaCall.getContainingMethod();
                    if (outerMethod.equals(method)) {
                        // lambdas can be recursive
                        outerMethod = null;
                        continue;
                    }
                    outerMethodParamLvtIndices = lambdaCall.getParamLvtIndices();
                    lambdaClosure = lambdaCall;
                    // there can only be 1 outer method
                    break;
                }
            }
        }

        final MethodNode node = ((AsmMethodData) method).getNode();
        final ClassData parentClass = method.parentClass();
        // A method cannot be both a lambda expression and a local class, so if we've already determined an outer
        // method, there's nothing to do here.
        Set<String> innerClassFieldNames = Set.of();
        @Nullable LocalClassClosure localClassClosure = null;
        int @Nullable [] innerClassOuterMethodParamLvtIndices = null;
        localCheck:
        if (outerMethod == null) {
            final @Nullable List<LocalClassClosure> localClasses = parentClass.get(HypoHydration.LOCAL_CLASSES);
            if (localClasses == null || localClasses.isEmpty()) {
                break localCheck;
            }

            localClassClosure = localClasses.get(0);
            outerMethod = (AsmMethodData) localClassClosure.getContainingMethod();
            // local classes don't capture as lvt, so don't assign `outerMethodParamLvtIndices`

            innerClassFieldNames = collectAllFields(parentClass);
            innerClassOuterMethodParamLvtIndices = localClassClosure.getParamLvtIndices();
        }

        // This method (`fillNames`) will ensure the outer method has names defined in its scope first.
        // If the scope is already computed this is a no-op
        if (outerMethod != null) {
            this.fillNames(outerMethod);
        }

        // We inherit names from our outer scope, if it exists. These names will be included in our scope for any
        // potential inner scopes (other nested lambdas or local classes) that are present in this method too
        final Set<String> scopedNames;
        if (outerMethod != null) {
            final @Nullable Set<String> outerScope = outerMethod.get(SCOPED_NAMES);
            scopedNames = new LinkedHashSet<>(outerScope == null ? Set.of() : outerScope);
        } else {
            scopedNames = new LinkedHashSet<>();
        }

        if (innerClassOuterMethodParamLvtIndices != null && !innerClassFieldNames.isEmpty()) {
            for (final LocalVariableNode outerLvt : outerMethod.getNode().localVariables) {
                if (find(innerClassOuterMethodParamLvtIndices, outerLvt.index) == -1) {
                    continue;
                }
                if (innerClassFieldNames.contains(outerLvt.name)) {
                    // We have a field in this local class which clashes with an outer variable.
                    // The only way to handle this kin of clash it to go back up and fix the
                    // local variable, we can't fix it here.
                    fixOuterScopeName(
                            new ClosureInfo(
                                    localClassClosure.getContainingMethod(), localClassClosure.getParamLvtIndices()),
                            outerLvt.name,
                            RootLvtSuggester.determineFinalName(outerLvt.name, scopedNames),
                            outerLvt.index);
                }
            }
        }

        final Optional<MethodMapping> methodMapping = this.mappings
                .getClassMapping(parentClass.name())
                .flatMap(c -> c.getMethodMapping(method.name(), method.descriptorText()));

        final @Nullable ClassData superClass = parentClass.superClass();

        if (this.reports.shouldGenerate(ReportType.MISSING_METHOD_PARAM)) {
            this.reportsInjector
                    .getInstance(MissingMethodParam.class)
                    .handleCheckingMappings(
                            method,
                            parentClass,
                            superClass,
                            lambdaCalls,
                            methodMapping.orElse(null),
                            outerMethodParamLvtIndices,
                            lambdaClosure,
                            localClassClosure);
        }

        // If there's no LVT table there's nothing for us to process
        if (node.localVariables == null) {
            // interface / abstract methods don't have LVT
            // But we still need to set param names
            final List<JvmType> paramTypes = method.descriptor().getParams();
            final int paramCount = paramTypes.size();

            if (node.parameters == null) {
                node.parameters = Arrays.asList(new ParameterNode[paramCount]);
            }

            for (int i = 0; i < paramCount; i++) {
                // always (i + 1) because abstract methods are never static
                final int fi = i;
                @Nullable
                String paramName = methodMapping
                        .flatMap(m -> m.getParameterMapping(fi + 1))
                        .map(Mapping::getDeobfuscatedName)
                        .orElse(null);

                if (paramName == null) {
                    paramName = this.lvtTypeSuggester.suggestNameFromType(paramTypes.get(i));
                }

                final String finalName = RootLvtSuggester.determineFinalName(paramName, scopedNames);
                if (node.parameters.get(i) == null) {
                    node.parameters.set(i, new ParameterNode(finalName, 0));
                } else {
                    node.parameters.get(i).name = finalName;
                }
            }

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

            final @Nullable String paramName = methodMapping
                    .flatMap(m -> m.getParameterMapping(lvt.index))
                    .map(Mapping::getDeobfuscatedName)
                    .orElse(null);

            @Nullable String mappedName = null;
            if (paramName != null) {
                mappedName = RootLvtSuggester.determineFinalName(paramName, scopedNames);
            }

            final String selectedName = mappedName != null
                    ? mappedName
                    : this.lvtAssignSuggester.suggestName(method, node, lvt, scopedNames);

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

    private static Set<String> collectAllFields(final ClassData classData) throws IOException {
        final HashSet<String> names = new HashSet<>();
        _collectAllFields(classData, classData, names);
        names.removeIf(n -> n.startsWith("val$") || n.startsWith("this$"));
        return names;
    }

    private static void _collectAllFields(final ClassData container, final ClassData current, final HashSet<String> res)
            throws IOException {
        final List<FieldData> fields = current.fields();
        if (container == current) {
            for (final FieldData field : fields) {
                res.add(field.name());
            }
        } else {
            for (final FieldData field : fields) {
                switch (field.visibility()) {
                    case PUBLIC, PROTECTED -> res.add(field.name());
                    case PACKAGE -> {
                        if (packageName(container).equals(packageName(current))) {
                            res.add(field.name());
                        }
                    }
                }
            }
        }

        final @Nullable ClassData superClass = current.superClass();
        if (superClass != null) {
            _collectAllFields(container, superClass, res);
        }
    }

    private static void fixOuterScopeName(
            final ClosureInfo closureInfo, final String badName, final String newName, final int lvtIndex) {
        final MethodData containing = closureInfo.containing();
        final MethodNode node = ((AsmMethodData) containing).getNode();

        for (final LocalVariableNode lvt : node.localVariables) {
            if (lvt.index == lvtIndex && lvt.name.equals(badName)) {
                lvt.name = newName;
            }
        }

        final int paramIndex = fromLvtToParamIndex(lvtIndex, containing);
        if (paramIndex != -1 && node.parameters != null) {
            node.parameters.get(paramIndex).name = newName;
        }

        // protect against `fillNames`
        synchronized (containing) {
            final @Nullable Set<String> containingScope = containing.get(SCOPED_NAMES);
            // should never be null
            if (containingScope != null) {
                containingScope.add(newName);
            }
        }

        final @Nullable List<LambdaClosure> lambdas = containing.get(HypoHydration.LAMBDA_CALLS);
        final @Nullable List<LocalClassClosure> localClasses = containing.get(HypoHydration.LOCAL_CLASSES);
        final ArrayList<ClosureInfo> innerClosureContainingMethods = new ArrayList<>(
                (lambdas != null ? lambdas.size() : 0) + (localClasses != null ? localClasses.size() : 0));
        if (lambdas != null) {
            for (final LambdaClosure lambda : lambdas) {
                if (!lambda.getContainingMethod().equals(containing)) {
                    innerClosureContainingMethods.add(
                            new ClosureInfo(lambda.getContainingMethod(), lambda.getParamLvtIndices()));
                }
            }
        }
        if (localClasses != null) {
            for (final LocalClassClosure localClass : localClasses) {
                if (!localClass.getContainingMethod().equals(containing)) {
                    innerClosureContainingMethods.add(
                            new ClosureInfo(localClass.getContainingMethod(), localClass.getParamLvtIndices()));
                }
            }
        }

        for (final ClosureInfo closure : innerClosureContainingMethods) {
            if (closure.paramLvtIndices().length > lvtIndex) {
                fixOuterScopeName(closure, badName, newName, closure.paramLvtIndices()[lvtIndex]);
            }
        }
    }

    private record ClosureInfo(MethodData containing, int[] paramLvtIndices) {}

    private static String packageName(final ClassData classData) {
        final String name = classData.name();
        final int lastIndex = name.lastIndexOf('/');
        if (lastIndex == -1) {
            return name;
        } else {
            return name.substring(0, lastIndex);
        }
    }

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
