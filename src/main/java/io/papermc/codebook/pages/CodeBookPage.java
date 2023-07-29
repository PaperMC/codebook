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

import com.google.inject.AbstractModule;
import com.google.inject.Key;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.binder.LinkedBindingBuilder;
import com.google.inject.util.Modules;
import com.google.inject.util.Providers;
import io.papermc.codebook.config.CodeBookContext;
import jakarta.inject.Qualifier;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.List;
import org.checkerframework.checker.nullness.qual.Nullable;

public abstract class CodeBookPage {
    private final IdentityHashMap<Key<?>, @Nullable Object> injections = new IdentityHashMap<>();

    public abstract void exec();

    public Module exec(final Module module) {
        this.injections.clear();
        this.exec();
        return Modules.override(module).with(this.nextModule());
    }

    public Module nextModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                CodeBookPage.this.injections.forEach((k, v) -> {
                    @SuppressWarnings("unchecked")
                    final var binding = (LinkedBindingBuilder<Object>) this.bind(k);
                    if (v == null) {
                        binding.toProvider(Providers.of(null));
                    } else {
                        binding.toInstance(v);
                    }
                });
            }
        };
    }

    public <T> Binding<T> bind(final Key<T> key) {
        return new Binding<>(key);
    }

    public final class Binding<T> {
        private final Key<T> key;

        public Binding(final Key<T> key) {
            this.key = key;
        }

        public void to(final @Nullable T value) {
            CodeBookPage.this.injections.put(this.key, value);
        }
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Context {
        Key<CodeBookContext> KEY = Key.get(CodeBookContext.class, Context.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface RemapperJar {
        Key<List<Path>> KEY = Key.get(new TypeLiteral<>() {}, RemapperJar.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface InputJar {
        Key<Path> KEY = Key.get(Path.class, InputJar.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ClasspathJars {
        Key<List<Path>> KEY = Key.get(new TypeLiteral<>() {}, ClasspathJars.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface MojangMappings {
        Key<Path> PATH_KEY = Key.get(Path.class, MojangMappings.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ParamMappings {
        Key<Path> PATH_KEY = Key.get(Path.class, ParamMappings.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface UnpickDefinitions {
        Key<Path> KEY = Key.get(Path.class, UnpickDefinitions.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface ConstantsJar {
        Key<Path> KEY = Key.get(Path.class, ConstantsJar.class);
    }

    @Qualifier
    @Target(ElementType.PARAMETER)
    @Retention(RetentionPolicy.RUNTIME)
    public @interface TempDir {
        Key<Path> KEY = Key.get(Path.class, TempDir.class);
    }
}
