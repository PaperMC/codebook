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

import dev.denwav.hypo.model.HypoModelUtil;
import io.papermc.codebook.exceptions.UnexpectedException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;
import org.checkerframework.checker.nullness.qual.Nullable;

public final class JarRunner {

    private final String name;
    private final List<Path> jars;
    private final List<String> arguments = new ArrayList<>();

    private JarRunner(final String name, final List<Path> jars) {
        this.name = name;
        this.jars = jars;
    }

    public static JarRunner of(final String name, final List<Path> jars) {
        return new JarRunner(name, jars);
    }

    public JarRunner withArgs(final String... args) {
        return this.withArgs(Arrays.asList(args));
    }

    public JarRunner withArgs(final Iterable<String> args) {
        for (final String arg : args) {
            this.arguments.add(arg);
        }
        return this;
    }

    public void run() {
        final URL[] urls;
        try {
            urls = this.jars.stream()
                    .map(HypoModelUtil.wrapFunction(j -> j.toUri().toURL()))
                    .toArray(URL[]::new);
        } catch (final Exception e) {
            throw new UnexpectedException("Failed to create URLs from " + this.jars, e);
        }

        try (final URLClassLoader loader = new URLClassLoader(urls)) {
            final Method mainMethod = this.getMainMethod(loader, this.jars);

            final Thread thread = this.createThread(mainMethod, loader);
            thread.start();
            try {
                thread.join();
            } catch (final InterruptedException e) {
                throw new UnexpectedException("Thread interrupted", e);
            }
        } catch (final IOException e) {
            throw new UnexpectedException("Failed to run jar", e);
        }
    }

    private Thread createThread(final Method mainMethod, final ClassLoader loader) {
        final Thread thread = new Thread(() -> {
            try {
                mainMethod.invoke(null, (Object) this.arguments.toArray(new String[0]));
            } catch (final IllegalAccessException e) {
                throw new UnexpectedException("Could not access main method", e);
            } catch (final InvocationTargetException e) {
                throw new UnexpectedException("Failure occurred while running jar", e.getCause());
            }
        });
        thread.setContextClassLoader(loader);
        thread.setDaemon(false);
        thread.setName(this.name + " thread");
        return thread;
    }

    private Method getMainMethod(final URLClassLoader loader, final List<Path> jars) throws IOException {
        for (final Path jar : jars) {
            final @Nullable Method mainMethod = this.getMainMethod0(loader, jar);
            if (mainMethod != null) {
                return mainMethod;
            }
        }

        throw new UnexpectedException("Failed to find main class in jars: " + jars);
    }

    private @Nullable Method getMainMethod0(final URLClassLoader loader, final Path jar) throws IOException {
        final @Nullable String mainClassName;
        try (final FileSystem fs = FileSystems.newFileSystem(jar)) {
            final Path manifest = fs.getPath("/", "META-INF", "MANIFEST.MF");
            if (Files.notExists(manifest)) {
                return null;
            }

            try (final InputStream input = Files.newInputStream(manifest)) {
                mainClassName = new Manifest(input).getMainAttributes().getValue("Main-Class");
            }
        }

        if (mainClassName == null) {
            return null;
        }

        final Class<?> mainClass;
        try {
            mainClass = Class.forName(mainClassName, true, loader);
        } catch (final ClassNotFoundException e) {
            return null;
        }

        try {
            return mainClass.getMethod("main", String[].class);
        } catch (final NoSuchMethodException e) {
            return null;
        }
    }
}
