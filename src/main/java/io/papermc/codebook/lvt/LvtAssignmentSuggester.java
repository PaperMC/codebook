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

import static dev.denwav.hypo.model.HypoModelUtil.wrapFunction;
import static io.papermc.codebook.lvt.LvtUtil.decapitalize;
import static io.papermc.codebook.lvt.LvtUtil.decapitalizeAlways;
import static io.papermc.codebook.lvt.LvtUtil.toJvmType;

import dev.denwav.hypo.asm.AsmClassData;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MemberData;
import dev.denwav.hypo.model.data.types.JvmType;
import dev.denwav.hypo.model.data.types.PrimitiveType;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;

public class LvtAssignmentSuggester {

    private static final List<NameSuggester> SUGGESTERS = List.of(
            LvtAssignmentSuggester::suggestGeneric,
            LvtAssignmentSuggester::suggestNameFromRecord,
            LvtAssignmentSuggester::suggestNameForRandomSource,
            LvtAssignmentSuggester::suggestNameForMcMthRandom,
            LvtAssignmentSuggester::suggestNameFromGetter,
            LvtAssignmentSuggester::suggestNameFromVerbBoolean,
            LvtAssignmentSuggester::suggestNameFromSingleWorldVerbBoolean,
            LvtAssignmentSuggester::suggestNameFromAs,
            LvtAssignmentSuggester::suggestNameFromNew,
            LvtAssignmentSuggester::suggestNameFromRead,
            LvtAssignmentSuggester::suggestNameFromLine,
            LvtAssignmentSuggester::suggestNameFromStrings);

    public final Map<String, AtomicInteger> missedNameSuggestions;

    public LvtAssignmentSuggester(final Map<String, AtomicInteger> missedNameSuggestions) {
        this.missedNameSuggestions = missedNameSuggestions;
    }

    public @Nullable String suggestNameFromAssignment(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn)
            throws IOException {

        for (final NameSuggester suggester : SUGGESTERS) {
            final @Nullable String suggestion = suggester.suggestName(context, owner, method, insn);
            if (suggestion != null) {
                return suggestion;
            }
        }
        this.missedNameSuggestions
                .computeIfAbsent(method.name() + "," + insn.owner + "," + insn.desc, (k) -> new AtomicInteger(0))
                .incrementAndGet();

        return null;
    }

    @FunctionalInterface
    private interface NameSuggester {

        @Nullable
        String suggestName(
                final @Nullable HypoContext context,
                final AsmClassData owner,
                final AsmMethodData method,
                final MethodInsnNode insn)
                throws IOException;
    }

    private static @Nullable String suggestGeneric(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        return switch (method.name()) {
            case "hashCode" -> "hashCode";
            case "size" -> "size";
            case "length" -> "len";
            case "freeze" -> "frozen";
            default -> null;
        };
    }

    private static @Nullable String suggestNameFromRecord(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        if (owner.kind() != ClassKind.RECORD) {
            return null;
        }
        final @Nullable List<FieldData> components = owner.recordComponents();
        if (components == null) {
            return null;
        }

        final String methodName = method.name();
        for (final FieldData component : components) {
            if (component.name().equals(methodName)) {
                return methodName;
            }
        }

        return null;
    }

    private static @Nullable String suggestNameForRandomSource(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn)
            throws IOException {
        final String methodName = method.name();
        if (insn.desc == null) return null;
        @Nullable String ownerClass = insn.owner;
        if (context != null) {
            final @Nullable ClassData randomSourceData =
                    context.getContextProvider().findClass("net/minecraft/util/RandomSource");
            if (randomSourceData != null && owner.doesExtendOrImplement(randomSourceData)) {
                ownerClass = "net/minecraft/util/RandomSource";
            }
        }

        if (!"net/minecraft/util/RandomSource".equals(ownerClass)) {
            return null;
        }

        if (!methodName.startsWith("next") || "next".equals(methodName)) {
            return null;
        }

        return createNextRandomName(methodName, insn);
    }

    private static @Nullable String suggestNameForMcMthRandom(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (!"net/minecraft/util/Mth".equals(insn.owner) || insn.desc == null) {
            return null;
        }

        if (!methodName.startsWith("next") || "next".equals(methodName)) {
            return null;
        }

        final List<JvmType> params = method.params();
        if (params.isEmpty() || !params.get(0).asInternalName().equals("Lnet/minecraft/util/RandomSource;")) {
            return null;
        }

        return createNextRandomName(methodName, insn);
    }

    private static @Nullable String createNextRandomName(final String methodName, final MethodInsnNode insn) {
        final @Nullable Predicate<String> expectedNextWord = expectedNextWordForRandomGen(insn);
        if (expectedNextWord == null) {
            return null;
        }

        final String nextWord = getNextWord("next".length(), methodName);
        if (expectedNextWord.test(nextWord)) {
            return "random" + nextWord;
        }
        return null;
    }

    private static Predicate<String> equalsAny(final String... strings) {
        return s -> Arrays.stream(strings).anyMatch(Predicate.isEqual(s));
    }

    private static @Nullable Predicate<String> expectedNextWordForRandomGen(final MethodInsnNode insn) {
        return switch (Type.getReturnType(insn.desc).getDescriptor()) {
            case "B" -> equalsAny("Byte");
            case "C" -> equalsAny("Char", "Character");
            case "D" -> equalsAny("Double");
            case "F" -> equalsAny("Float");
            case "I" -> equalsAny("Int", "Integer");
            case "J" -> equalsAny("Long");
            case "S" -> equalsAny("Short");
            case "Z" -> equalsAny("Bool", "Boolean");
            default -> null;
        };
    }

    private static String getNextWord(final int start, final String str) {
        final StringBuilder nextWord = new StringBuilder();
        for (int i = start; i < str.length(); i++) {
            final char ch = str.charAt(i);
            if (nextWord.isEmpty()) {
                nextWord.append(ch);
            } else if (!Character.isUpperCase(ch)) {
                nextWord.append(ch);
            } else {
                break;
            }
        }
        return nextWord.toString();
    }

    private static @Nullable String suggestNameFromGetter(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (!methodName.startsWith("get") || methodName.equals("get")) {
            // If the method isn't `get<Thing>` - or if the method is just `get`
            return null;
        }

        int index = 3;
        if (methodName.startsWith("getOrCreate")) {
            if (methodName.equals("getOrCreate")) {
                return null;
            }
            index = 11;
        }

        return decapitalize(methodName, index);
    }

    private static final List<String> BOOL_METHOD_PREFIXES = List.of("is", "has", "can", "should");

    private static @Nullable String suggestNameFromVerbBoolean(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        if (method.returnType() != PrimitiveType.BOOLEAN) {
            return null;
        }
        final String methodName = method.name();

        @Nullable String prefix = null;
        for (final String possiblePrefix : BOOL_METHOD_PREFIXES) {
            if (!possiblePrefix.equals(methodName) && methodName.startsWith(possiblePrefix)) {
                prefix = possiblePrefix;
                break;
            }
        }
        if (prefix == null) {
            return null;
        }

        if (Character.isUpperCase(methodName.charAt(prefix.length()))) {
            return methodName;
        } else {
            return null;
        }
    }

    private static @Nullable String suggestNameFromSingleWorldVerbBoolean(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn)
            throws IOException {
        if (method.returnType() != PrimitiveType.BOOLEAN) {
            return null;
        }
        final String methodName = method.name();

        final String prefix;
        if (methodName.equals("is")) {
            prefix = "is";
        } else if (methodName.equals("has")) {
            prefix = "has";
        } else {
            return null;
        }

        final List<JvmType> paramTypes = method.params();
        if (paramTypes.size() != 1) {
            return null;
        }
        final String paramTypeDesc = paramTypes.get(0).asInternalName();

        final AbstractInsnNode prev = insn.getPrevious();
        if (prev instanceof final FieldInsnNode fieldInsnNode
                && fieldInsnNode.getOpcode() == Opcodes.GETSTATIC
                && fieldInsnNode.name != null
                && isStringAllUppercase(fieldInsnNode.name)) {

            final boolean isFinal = Optional.ofNullable(context)
                    .map(wrapFunction(ctx -> ctx.getContextProvider().findClass(fieldInsnNode.owner)))
                    .map(fieldOwner -> fieldOwner.field(fieldInsnNode.name, toJvmType(fieldInsnNode.desc)))
                    .map(MemberData::isFinal)
                    .orElse(false);
            if (!isFinal) {
                return null;
            }

            return prefix + convertStaticFieldNameToLocalVarName(fieldInsnNode);
        } else {
            if ("Lnet/minecraft/tags/TagKey;".equals(paramTypeDesc)) { // isTag is better than isTagKey
                return "isTag";
            }
            final String typeName = LvtTypeSuggester.suggestNameFromType(context, toJvmType(paramTypeDesc));
            return prefix + decapitalizeAlways(typeName, 0);
        }
    }

    private static String convertStaticFieldNameToLocalVarName(final FieldInsnNode fieldInsnNode) {
        final StringBuilder output = new StringBuilder();
        for (final String s : fieldInsnNode.name.split("_")) {
            output.append(s.charAt(0)).append(s.substring(1).toLowerCase(Locale.ENGLISH));
        }
        return output.toString();
    }

    private static boolean isStringAllUppercase(final String input) {
        for (int i = 0; i < input.length(); i++) {
            final char ch = input.charAt(i);
            if (Character.isAlphabetic(ch)) {
                if (!Character.isUpperCase(ch)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static @Nullable String suggestNameFromAs(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (!methodName.startsWith("as") || methodName.equals("as")) {
            return null;
        }

        return decapitalize(methodName, 2);
    }

    private static @Nullable String suggestNameFromNew(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (!methodName.startsWith("new") || methodName.equals("new")) {
            return null;
        }

        final @Nullable String result =
                switch (insn.owner) {
                    case "com/google/common/collect/Lists" -> "list";
                    case "com/google/common/collect/Maps" -> "map";
                    case "com/google/common/collect/Sets" -> "set";
                    default -> null;
                };
        if (result != null) {
            return result;
        }

        return decapitalize(methodName, 3);
    }

    private static @Nullable String suggestNameFromRead(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (!methodName.startsWith("read") || methodName.equals("read")) {
            return null;
        }

        return decapitalize(methodName, 4);
    }

    private static @Nullable String suggestNameFromLine(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();
        if (methodName.equals("readLine")) {
            return "line";
        }
        return null;
    }

    private static @Nullable String suggestNameFromStrings(
            final @Nullable HypoContext context,
            final AsmClassData owner,
            final AsmMethodData method,
            final MethodInsnNode insn) {
        final String methodName = method.name();

        if (methodName.startsWith("split")) {
            if (owner.name().equals("java/lang/String") || owner.name().equals("com/google/common/base/Splitter")) {
                return "parts";
            }
        }

        if (methodName.equals("repeat") && method.returnType().asInternalName().equals("Ljava/lang/String;")) {
            return "repeated";
        }
        if (methodName.equals("indexOf") || methodName.equals("lastIndexOf")) {
            return "index";
        }
        if (methodName.equals("substring")) {
            return "sub";
        }
        if (methodName.equals("codePointAt")) {
            return "code";
        }
        if (methodName.equals("trim")) {
            return "trimmed";
        }
        if (methodName.startsWith("strip")) {
            return "stripped";
        }
        if (methodName.equals("formatted")) {
            return "formatted";
        }

        return null;
    }
}
