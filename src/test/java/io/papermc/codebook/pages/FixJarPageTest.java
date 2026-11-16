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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.denwav.hypo.asm.AsmClassDataProvider;
import dev.denwav.hypo.asm.AsmMethodData;
import dev.denwav.hypo.asm.hydrate.BridgeMethodHydrator;
import dev.denwav.hypo.core.HypoConfig;
import dev.denwav.hypo.core.HypoContext;
import dev.denwav.hypo.hydrate.HydrationManager;
import dev.denwav.hypo.model.ClassProviderRoot;
import dev.denwav.hypo.model.data.ClassData;
import dev.denwav.hypo.model.data.MethodData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FixJarPageTest {

    private static final String OVERRIDE = "Ljava/lang/Override;";

    private static final Map<String, String> SOURCES = Map.ofEntries(
            Map.entry("p/Generic.java", """
                    package p;
                    public interface Generic<T> { void m(T t); }
                    """),
            Map.entry("p/Exact.java", """
                    package p;
                    public interface Exact { void m(String s); }
                    """),
            Map.entry("p/Unrelated.java", """
                    package p;
                    public class Unrelated {
                        public void m(String s) {}
                        @Override public String toString() { return ""; }
                    }
                    """),
            Map.entry("p/UnrelatedSub.java", """
                    package p;
                    public class UnrelatedSub extends Unrelated implements Generic<String> {}
                    """),
            Map.entry("p/ImplementsExact.java", """
                    package p;
                    public class ImplementsExact implements Exact {
                        @Override public void m(String s) {}
                    }
                    """),
            Map.entry("p/ImplementsExactSub.java", """
                    package p;
                    public class ImplementsExactSub extends ImplementsExact implements Generic<String> {}
                    """),
            Map.entry("p/Concrete.java", """
                    package p;
                    public class Concrete implements Generic<String> {
                        @Override public void m(String s) {}
                    }
                    """),
            Map.entry("p/InterfaceAbstract.java", """
                    package p;
                    public interface InterfaceAbstract extends Generic<String> { @Override void m(String s); }
                    """),
            Map.entry("p/InterfaceDefault.java", """
                    package p;
                    public interface InterfaceDefault extends Generic<String> { @Override default void m(String s) {} }
                    """),
            Map.entry("p/AbstractClass.java", """
                    package p;
                    public abstract class AbstractClass implements Generic<String> { @Override public abstract void m(String s); }
                    """),
            Map.entry("p/Bounded.java", """
                    package p;
                    public interface Bounded<U extends Number> extends Generic<U> { @Override void m(U u); }
                    """),
            Map.entry("p/Covariant.java", """
                    package p;
                    public interface Covariant { Object get(); }
                    """),
            Map.entry("p/CovariantInterface.java", """
                    package p;
                    public interface CovariantInterface extends Covariant { @Override String get(); }
                    """),
            Map.entry("p/CovariantAbstract.java", """
                    package p;
                    public abstract class CovariantAbstract implements Covariant { @Override public abstract String get(); }
                    """),
            Map.entry("p/Overload.java", """
                    package p;
                    public class Overload implements Generic<String> {
                        @Override public void m(String s) {}
                        public void m(Integer i) {}
                    }
                    """),
            Map.entry("p/RecordImpl.java", """
                    package p;
                    public record RecordImpl(String m) implements java.util.function.Supplier<String> {
                        @Override public String get() { return this.m; }
                    }
                    """),
            Map.entry("p/ObjectMethods.java", """
                    package p;
                    public interface ObjectMethods {
                        @Override String toString();
                        @Override boolean equals(Object o);
                        Object clone();
                        void finalize();
                    }
                    """),
            Map.entry("p/CovariantClone.java", """
                    package p;
                    public interface CovariantClone { CovariantClone clone(); }
                    """),
            Map.entry("p/DeclaresClone.java", """
                    package p;
                    public interface DeclaresClone { Object clone(); }
                    """),
            Map.entry("p/InheritsClone.java", """
                    package p;
                    public interface InheritsClone extends DeclaresClone { @Override Object clone(); }
                    """),
            Map.entry("p/InheritsCloneCovariant.java", """
                    package p;
                    public interface InheritsCloneCovariant extends DeclaresClone { @Override InheritsCloneCovariant clone(); }
                    """),
            Map.entry("p/ClassClone.java", """
                    package p;
                    public class ClassClone { @Override public Object clone() { return this; } }
                    """),
            Map.entry("p/Members.java", """
                    package p;
                    public class Members {
                        public static void hidden() {}
                        private void priv() {}
                        void packagePrivate() {}
                    }
                    """),
            Map.entry("p/MembersSub.java", """
                    package p;
                    public class MembersSub extends Members {
                        public static void hidden() {}
                        private void priv() {}
                    }
                    """),
            Map.entry("q/OtherPackage.java", """
                    package q;
                    public class OtherPackage extends p.Members { public void packagePrivate() {} }
                    """),
            Map.entry("q/OtherPackageEmpty.java", """
                    package q;
                    public class OtherPackageEmpty extends p.Members {}
                    """),
            Map.entry("p/BackInPackage.java", """
                    package p;
                    public class BackInPackage extends q.OtherPackageEmpty { @Override void packagePrivate() {} }
                    """));

    @TempDir
    static Path tempDir;

    private static HypoContext context;

    @BeforeAll
    static void processFixtures() throws Exception {
        final Path sourceDir = tempDir.resolve("src");
        final Path classesDir = tempDir.resolve("classes");
        Files.createDirectories(classesDir);

        final List<String> args = new ArrayList<>(List.of("-proc:none", "-nowarn", "-d", classesDir.toString()));
        for (final var entry : SOURCES.entrySet()) {
            final Path file = sourceDir.resolve(entry.getKey());
            Files.createDirectories(file.getParent());
            Files.writeString(file, entry.getValue());
            args.add(file.toString());
        }

        // The fixtures declare @Override wherever javac allows it, so compiling them also checks the expectations below
        final var compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "Tests must run on a JDK");
        assertEquals(0, compiler.run(null, null, null, args.toArray(new String[0])), "Fixtures failed to compile");

        context = HypoContext.builder()
                .withProvider(AsmClassDataProvider.of(ClassProviderRoot.fromDir(classesDir)))
                .withContextProvider(AsmClassDataProvider.of(ClassProviderRoot.ofJdk()))
                .withConfig(HypoConfig.builder().build())
                .build();
        HydrationManager.createDefault().register(BridgeMethodHydrator.create()).hydrate(context);

        new FixJarPage(context).exec();
    }

    @AfterAll
    static void closeContext() throws Exception {
        if (context != null) {
            context.close();
        }
    }

    @ParameterizedTest(name = "{0}#{1}{2}")
    @CsvSource({
        // A class with a bridge in a subclass must not stop processing for the rest of the class
        "p/Unrelated, toString, ()Ljava/lang/String;",
        // A bridge in a subclass doesn't change that the method overrides something itself
        "p/ImplementsExact, m, (Ljava/lang/String;)V",
        // Different descriptors, linked by the bridge javac generates
        "p/Concrete, m, (Ljava/lang/String;)V",
        "p/InterfaceAbstract, m, (Ljava/lang/String;)V",
        "p/InterfaceDefault, m, (Ljava/lang/String;)V",
        "p/AbstractClass, m, (Ljava/lang/String;)V",
        "p/Bounded, m, (Ljava/lang/Number;)V",
        "p/CovariantInterface, get, ()Ljava/lang/String;",
        "p/CovariantAbstract, get, ()Ljava/lang/String;",
        "p/Overload, m, (Ljava/lang/String;)V",
        "p/RecordImpl, get, ()Ljava/lang/String;",
        "p/RecordImpl, toString, ()Ljava/lang/String;",
        // Interfaces may override the public methods of Object (JLS 9.6.4.4)
        "p/ObjectMethods, toString, ()Ljava/lang/String;",
        "p/ObjectMethods, equals, (Ljava/lang/Object;)Z",
        // Declared by a superinterface, even though Object has a matching non-public method too
        "p/InheritsClone, clone, ()Ljava/lang/Object;",
        "p/InheritsCloneCovariant, clone, ()Lp/InheritsCloneCovariant;",
        "p/ClassClone, clone, ()Ljava/lang/Object;",
        // Same package as the package-private method, with a class from another package in between (JLS 8.4.8.1)
        "p/BackInPackage, packagePrivate, ()V",
    })
    void annotatesOverrides(final String owner, final String name, final String descriptor) throws IOException {
        assertTrue(hasOverride(owner, name, descriptor));
    }

    @ParameterizedTest(name = "{0}#{1}{2}")
    @CsvSource({
        // Only implements the interface through the subclass, @Override here wouldn't compile
        "p/Unrelated, m, (Ljava/lang/String;)V",
        "p/Overload, m, (Ljava/lang/Integer;)V",
        // Not inherited by interfaces, since they aren't public (JLS 9.2, 9.6.4.4)
        "p/ObjectMethods, clone, ()Ljava/lang/Object;",
        "p/ObjectMethods, finalize, ()V",
        "p/CovariantClone, clone, ()Lp/CovariantClone;",
        "p/DeclaresClone, clone, ()Ljava/lang/Object;",
        // Hiding, private, and across packages (JLS 8.4.8.1)
        "p/MembersSub, hidden, ()V",
        "p/MembersSub, priv, ()V",
        "q/OtherPackage, packagePrivate, ()V",
        // Not declared by the fixture's source: bridges and the implicit members of the record
        "p/Concrete, m, (Ljava/lang/Object;)V",
        "p/ImplementsExactSub, m, (Ljava/lang/Object;)V",
        "p/UnrelatedSub, m, (Ljava/lang/Object;)V",
        "p/InterfaceAbstract, m, (Ljava/lang/Object;)V",
        "p/CovariantInterface, get, ()Ljava/lang/Object;",
        "p/RecordImpl, get, ()Ljava/lang/Object;",
        "p/RecordImpl, m, ()Ljava/lang/String;",
    })
    void doesNotAnnotateOtherMethods(final String owner, final String name, final String descriptor)
            throws IOException {
        assertFalse(hasOverride(owner, name, descriptor));
    }

    private static boolean hasOverride(final String owner, final String name, final String descriptor)
            throws IOException {
        final ClassData classData = context.getProvider().findClass(owner);
        assertNotNull(classData, owner);
        for (final MethodData method : classData.methods(name)) {
            if (method.descriptorText().equals(descriptor)) {
                final var annotations = ((AsmMethodData) method).getNode().invisibleAnnotations;
                return annotations != null && annotations.stream().anyMatch(a -> a.desc.equals(OVERRIDE));
            }
        }
        throw new AssertionError("No method " + owner + "#" + name + descriptor);
    }
}
