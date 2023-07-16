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

import static dev.denwav.hypo.asm.HypoAsmUtil.toJvmType;
import static dev.denwav.hypo.model.data.MethodDescriptor.parseDescriptor;
import static io.papermc.codebook.util.IOUtil.copy;
import static io.papermc.codebook.util.IOUtil.createParentDirectories;
import static java.util.Objects.requireNonNull;
import static java.util.Objects.requireNonNullElse;
import static org.objectweb.asm.Type.getType;

import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.hydrate.BridgeMethodHydrator;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.hydrate.generic.HypoHydration;
import dev.denwav.hypo.model.ClassProviderRoot;
import dev.denwav.hypo.model.HypoModelUtil;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.ClassKind;
import dev.denwav.hypo.model.data.FieldData;
import dev.denwav.hypo.model.data.MethodData;
import dev.denwav.hypo.model.data.Visibility;
import io.papermc.codebook.exceptions.UnexpectedException;
import jakarta.inject.Inject;
import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

public final class FixJarPage extends CodeBookPage {

    private final Path inputJar;
    private final List<Path> classpath;
    private final Path tempDir;

    @Inject
    public FixJarPage(
            @InputJar final Path inputJar, @ClasspathJars final List<Path> classpath, @TempDir final Path tempDir) {
        this.inputJar = inputJar;
        this.classpath = classpath;
        this.tempDir = tempDir;
    }

    @Override
    public void exec() {
        final HypoContext context;
        try {
            context = this.createContext();
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create context for bytecode analysis", e);
        }

        try (context) {
            HydrationManager.createDefault()
                    .register(BridgeMethodHydrator.create())
                    .hydrate(context);

            final Path result = this.fixWithContext(context);
            this.bind(InputJar.KEY).to(result);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to fix jar", e);
        }
    }

    private HypoContext createContext() throws IOException {
        return HypoContext.builder()
                .withProvider(AsmClassDataProvider.of(ClassProviderRoot.fromJar(this.inputJar)))
                .withContextProvider(AsmClassDataProvider.of(this.classpath.stream()
                        .map(HypoModelUtil.wrapFunction(ClassProviderRoot::fromJar))
                        .toList()))
                .withContextProvider(AsmClassDataProvider.of(ClassProviderRoot.ofJdk()))
                .build();
    }

    private Path fixWithContext(final HypoContext context) {
        final Path fixedJar = this.tempDir.resolve("fixed.jar");
        try (final FileSystem inFs = FileSystems.newFileSystem(this.inputJar);
                final FileSystem outFs = FileSystems.newFileSystem(fixedJar, Map.of("create", true))) {
            this.fixToOutput(context, inFs, outFs);
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to create fixed jar", e);
        }

        return fixedJar;
    }

    private void fixToOutput(final HypoContext context, final FileSystem inFs, final FileSystem outFs)
            throws IOException {
        try (final Stream<Path> stream = Files.walk(inFs.getPath("/"))) {
            stream.forEach(HypoModelUtil.wrapConsumer(p -> {
                this.processFile(context, p, outFs);
            }));
        }
    }

    private void processFile(final HypoContext context, final Path inputFile, final FileSystem outFs)
            throws IOException {
        if (!Files.isRegularFile(inputFile)) {
            return;
        }

        final Path outputFile = outFs.getPath(inputFile.toString());
        createParentDirectories(outputFile);

        if (!inputFile.getFileName().toString().endsWith(".class")) {
            copy(inputFile, outputFile);
            return;
        }

        final ClassReader reader = new ClassReader(Files.readAllBytes(inputFile));
        final ClassNode node = new ClassNode(Opcodes.ASM9);
        reader.accept(node, 0);

        // This results in the class file being read and parsed by asm twice
        // Really not great, but it's pretty minor all things considered. We need to make modifications
        // to the node, but that's not a use-case hypo supports. Still want to use hypo rather than re-implementing
        // the analysis, so here we are
        final @Nullable ClassData classData = context.getProvider().findClass(node.name);
        if (classData == null) {
            throw new UnexpectedException("Could not find existing class");
        }

        this.processClass(classData, node);

        final ClassWriter writer = new ClassWriter(0);
        node.accept(writer);

        Files.write(outputFile, writer.toByteArray());
    }

    private void processClass(final ClassData classData, final ClassNode node) {
        OverrideAnnotationAdder.addAnnotations(classData, node);
        RecordFieldAccessFixer.fixClass(classData, node);
    }

    private static final int RESET_ACCESS = ~(Opcodes.ACC_PUBLIC | Opcodes.ACC_PRIVATE | Opcodes.ACC_PROTECTED);

    private static final class OverrideAnnotationAdder {
        private static final int DISQUALIFIED_METHODS = Opcodes.ACC_STATIC | Opcodes.ACC_PRIVATE;

        private OverrideAnnotationAdder() {}

        private static void addAnnotations(final ClassData classData, final ClassNode node) {
            for (final MethodNode method : node.methods) {
                if ((method.access & DISQUALIFIED_METHODS) != 0) {
                    continue;
                }
                if (isInitializer(method)) {
                    continue;
                }

                final MethodData methodData =
                        requireNonNull(classData.method(method.name, parseDescriptor(method.desc)));

                final @Nullable MethodData targetMethodData = methodData.get(HypoHydration.SYNTHETIC_SOURCE);
                final MethodData baseMethod = requireNonNullElse(targetMethodData, methodData);

                if (baseMethod.superMethod() != null) {
                    if (method.invisibleAnnotations == null) {
                        method.invisibleAnnotations = new ArrayList<>();
                    }
                    final var annoClass = "Ljava/lang/Override;";
                    if (method.invisibleAnnotations.stream().noneMatch(a -> a.desc.equals(annoClass))) {
                        method.invisibleAnnotations.add(new AnnotationNode(annoClass));
                    }
                }
            }
        }

        private static boolean isInitializer(final MethodNode method) {
            return method.name.equals("<init>") || method.name.equals("<clinit>");
        }
    }

    private static final class RecordFieldAccessFixer {
        private RecordFieldAccessFixer() {}

        private static void fixClass(final ClassData classData, final ClassNode node) {
            if (classData.kind() != ClassKind.RECORD) {
                return;
            }

            for (final FieldNode field : node.fields) {
                final FieldData fieldData = requireNonNull(classData.field(field.name, toJvmType(getType(field.desc))));
                // just a little nicer to read using hypo vs querying flags
                if (!fieldData.isStatic() && fieldData.visibility() != Visibility.PRIVATE && fieldData.isFinal()) {
                    field.access = (field.access & RESET_ACCESS) | Opcodes.ACC_PRIVATE;
                }
            }
        }
    }
}
