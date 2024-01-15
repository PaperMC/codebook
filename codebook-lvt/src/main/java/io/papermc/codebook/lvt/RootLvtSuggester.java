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

import static dev.denwav.hypo.model.data.MethodDescriptor.parseDescriptor;
import static io.papermc.codebook.lvt.LvtUtil.toJvmType;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.MethodDescriptor;
import dev.denwav.hypo.model.data.types.JvmType;
import io.papermc.codebook.lvt.suggestion.ComplexGetSuggester;
import io.papermc.codebook.lvt.suggestion.FluentGetterSuggester;
import io.papermc.codebook.lvt.suggestion.GenericSuggester;
import io.papermc.codebook.lvt.suggestion.LvtSuggester;
import io.papermc.codebook.lvt.suggestion.MathSuggester;
import io.papermc.codebook.lvt.suggestion.NewPrefixSuggester;
import io.papermc.codebook.lvt.suggestion.PositionsSuggester;
import io.papermc.codebook.lvt.suggestion.RecordComponentSuggester;
import io.papermc.codebook.lvt.suggestion.SingleVerbBooleanSuggester;
import io.papermc.codebook.lvt.suggestion.SingleVerbSuggester;
import io.papermc.codebook.lvt.suggestion.StringSuggester;
import io.papermc.codebook.lvt.suggestion.VerbPrefixBooleanSuggester;
import io.papermc.codebook.lvt.suggestion.context.ContainerContext;
import io.papermc.codebook.lvt.suggestion.context.field.FieldCallContext;
import io.papermc.codebook.lvt.suggestion.context.field.FieldInsnContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodCallContext;
import io.papermc.codebook.lvt.suggestion.context.method.MethodInsnContext;
import io.papermc.codebook.lvt.suggestion.numbers.MthRandomSuggester;
import io.papermc.codebook.lvt.suggestion.numbers.RandomSourceSuggester;
import io.papermc.codebook.report.Reports;
import io.papermc.codebook.report.type.MissingMethodLvtSuggestion;
import java.io.IOException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

public final class RootLvtSuggester extends AbstractModule implements LvtSuggester {

    // the order of these is somewhat important. Generally, owning-class-specific suggesters
    // should be first, like RandomSource or Mth. Then more general suggesters that only check
    // the method name should follow.
    private static final List<Class<? extends LvtSuggester>> SUGGESTERS = List.of(
            RandomSourceSuggester.class,
            MthRandomSuggester.class,
            MathSuggester.class,
            StringSuggester.class,
            PositionsSuggester.class,
            ComplexGetSuggester.class,
            NewPrefixSuggester.class,
            SingleVerbSuggester.class,
            VerbPrefixBooleanSuggester.class,
            SingleVerbBooleanSuggester.class,
            FluentGetterSuggester.class,
            RecordComponentSuggester.class,
            GenericSuggester.class);

    private final HypoContext hypoContext;
    private final LvtTypeSuggester lvtTypeSuggester;
    private final Injector injector;
    private final List<? extends LvtSuggester> suggesters;

    public RootLvtSuggester(
            final HypoContext hypoContext, final LvtTypeSuggester lvtTypeSuggester, final Reports reports) {
        this.hypoContext = hypoContext;
        this.lvtTypeSuggester = lvtTypeSuggester;
        this.injector = Guice.createInjector(this, reports);
        this.suggesters = SUGGESTERS.stream().map(this.injector::getInstance).toList();
    }

    @Override
    protected void configure() {
        this.bind(HypoContext.class).toInstance(this.hypoContext);
        this.bind(LvtTypeSuggester.class).toInstance(this.lvtTypeSuggester);
    }

    public String suggestName(
            final MethodData parent, final MethodNode node, final LocalVariableNode lvt, final Set<String> scopedNames)
            throws IOException {
        @Nullable VarInsnNode assignmentNode = null;
        // `insn` could represent the first instruction, so check if there actually is a previous instruction
        if (lvt.start.getPrevious() != null) {
            // In most cases the store instruction for a new local variable will happen directly before the label which
            // marks the variable's start.
            // We do this quick check before checking all instructions as a fallback.
            // The most common exception is parameters, which start at 0
            final AbstractInsnNode prev = lvt.start.getPrevious();
            final int op = prev.getOpcode();
            if (op >= Opcodes.ISTORE && op <= Opcodes.ASTORE) {
                final var varInsn = (VarInsnNode) prev;
                if (varInsn.var == lvt.index) {
                    assignmentNode = varInsn;
                }
            }
        }

        if (assignmentNode == null) {
            for (final AbstractInsnNode insn : node.instructions) {
                final int op = insn.getOpcode();
                if (op < Opcodes.ISTORE || op > Opcodes.ASTORE) {
                    continue;
                }

                final var varInsn = (VarInsnNode) insn;
                if (varInsn.var == lvt.index) {
                    assignmentNode = varInsn;
                    break;
                }
            }
        }

        if (assignmentNode != null) {
            final @Nullable String suggestedName = this.suggestNameFromFirstAssignment(parent, assignmentNode);
            if (suggestedName != null) {
                return determineFinalName(suggestedName, scopedNames);
            }
        }

        // we couldn't determine a name from the assignment, so determine a name from the type
        final JvmType lvtType = toJvmType(lvt.desc);
        return determineFinalName(this.lvtTypeSuggester.suggestNameFromType(lvtType), scopedNames);
    }

    public static String determineFinalName(final String suggestedName, final Set<String> scopedNames) {
        final String name;
        if (JAVA_KEYWORDS.contains(suggestedName)) {
            name = "_" + suggestedName;
        } else {
            name = suggestedName;
        }

        if (scopedNames.add(name)) {
            return name;
        }

        int counter = 1;
        while (true) {
            final String nextSuggestedName = name + counter;
            if (scopedNames.add(nextSuggestedName)) {
                return nextSuggestedName;
            }
            counter++;
        }
    }

    private static final Set<BoxMethod> BOX_METHODS = Set.of(
            new BoxMethod("java/lang/Byte", "byteValue", "()B"),
            new BoxMethod("java/lang/Short", "shortValue", "()S"),
            new BoxMethod("java/lang/Integer", "intValue", "()I"),
            new BoxMethod("java/lang/Long", "longValue", "()J"),
            new BoxMethod("java/lang/Float", "floatValue", "()F"),
            new BoxMethod("java/lang/Double", "doubleValue", "()D"),
            new BoxMethod("java/lang/Boolean", "booleanValue", "()Z"),
            new BoxMethod("java/lang/Character", "charValue", "()C")
    );
    private static final Set<String> BOX_METHOD_NAMES = BOX_METHODS.stream().map(BoxMethod::name).collect(Collectors.toUnmodifiableSet());

    private record BoxMethod(String owner, String name, String desc) {
        boolean is(final MethodInsnNode node) {
            return this.owner.equals(node.owner) && this.name.equals(node.name) && this.desc.equals(node.desc) && !node.itf;
        }
    }

    private @Nullable AbstractInsnNode walkBack(final VarInsnNode assignmentNode) {
        AbstractInsnNode prev = assignmentNode.getPrevious();
        if (prev != null) {
            final int op = prev.getOpcode();
            if (op == Opcodes.INVOKEVIRTUAL) {
                final MethodInsnNode methodInsnNode = (MethodInsnNode) prev;
                if (BOX_METHOD_NAMES.contains(methodInsnNode.name) && BOX_METHODS.stream().anyMatch(bm -> bm.is(methodInsnNode))) {
                    prev = prev.getPrevious();
                    if (prev != null && prev.getOpcode() == Opcodes.CHECKCAST) {
                        return prev.getPrevious();
                    }
                    return prev;
                }
            }
            return prev;
        }
        return null;
    }

    private @Nullable String suggestNameFromFirstAssignment(final MethodData parent, final VarInsnNode varInsn)
            throws IOException {
        final @Nullable AbstractInsnNode prev = this.walkBack(varInsn);
        if (prev == null) {
            return null;
        }
        final int op = prev.getOpcode();
        if (op != Opcodes.INVOKESTATIC && op != Opcodes.INVOKEVIRTUAL && op != Opcodes.INVOKEINTERFACE) {
            return null;
        }

        final MethodInsnNode methodInsnNode = (MethodInsnNode) prev;

        final @Nullable ClassData owner = this.hypoContext.getContextProvider().findClass(methodInsnNode.owner);
        if (owner == null) {
            return null;
        }
        final @Nullable MethodData method =
                findMethod(owner, methodInsnNode.name, parseDescriptor(methodInsnNode.desc));
        if (method == null) {
            return null;
        }

        return this.suggestFromMethod(
                MethodCallContext.create(method),
                MethodInsnContext.create(owner, methodInsnNode),
                ContainerContext.from(parent));
    }

    @Override
    public @Nullable String suggestFromMethod(
            final MethodCallContext call, final MethodInsnContext insn, final ContainerContext container)
            throws IOException {
        @Nullable String suggestion;
        for (final LvtSuggester delegate : this.suggesters) {
            suggestion = delegate.suggestFromMethod(call, insn, container);
            if (suggestion != null) {
                return suggestion;
            }
        }
        this.injector
                .getInstance(MissingMethodLvtSuggestion.class)
                .reportMissingMethodLvtSuggestion(call.data(), insn.node());
        return null;
    }

    @Override
    public @Nullable String suggestFromField(
            final FieldCallContext call, final FieldInsnContext insn, final ContainerContext container)
            throws IOException {
        @Nullable String suggestion;
        for (final LvtSuggester delegate : this.suggesters) {
            suggestion = delegate.suggestFromField(call, insn, container);
            if (suggestion != null) {
                return suggestion;
            }
        }
        return null;
    }

    private static @Nullable MethodData findMethod(
            final @Nullable ClassData data, final String name, final MethodDescriptor desc) throws IOException {
        if (data == null) {
            return null;
        }

        {
            final @Nullable MethodData method = data.method(name, desc);
            if (method != null) {
                return method;
            }
        }

        final @Nullable ClassData superClass = data.superClass();
        if (superClass != null) {
            final @Nullable MethodData method = findMethod(superClass, name, desc);
            if (method != null) {
                return method;
            }
        }

        for (final ClassData anInterface : data.interfaces()) {
            final @Nullable MethodData method = findMethod(anInterface, name, desc);
            if (method != null) {
                return method;
            }
        }

        return null;
    }

    private static final Set<String> JAVA_KEYWORDS = Set.of(
            "abstract",
            "continue",
            "for",
            "new",
            "switch",
            "assert",
            "default",
            "goto",
            "package",
            "synchronized",
            "boolean",
            "do",
            "if",
            "private",
            "this",
            "break",
            "double",
            "implements",
            "protected",
            "throw",
            "byte",
            "else",
            "import",
            "public",
            "throws",
            "case",
            "enum",
            "instanceof",
            "return",
            "transient",
            "catch",
            "extends",
            "int",
            "short",
            "try",
            "char",
            "final",
            "interface",
            "static",
            "void",
            "class",
            "finally",
            "long",
            "strictfp",
            "volatile",
            "const",
            "float",
            "native",
            "super",
            "while",
            "exports",
            "module",
            "non-sealed",
            "open",
            "opens",
            "permits",
            "provides",
            "record",
            "sealed",
            "transitive",
            "uses",
            "var",
            "with",
            "yield");
}
