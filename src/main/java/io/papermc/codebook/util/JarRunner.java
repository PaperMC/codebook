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
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.jar.Manifest;

public final class JarRunner {

    private final String name;
    private final Path jar;
    private final List<String> arguments = new ArrayList<>();

    private JarRunner(final String name, final Path jar) {
        this.name = name;
        this.jar = jar;
    }

    public static JarRunner of(final String name, final Path jar) {
        return new JarRunner(name, jar);
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
        final URL url;
        try {
            url = this.jar.toUri().toURL();
        } catch (final MalformedURLException e) {
            throw new UnexpectedException("Failed to create URL from " + this.jar, e);
        }

        try (final URLClassLoader loader = new URLClassLoader(new URL[] {url})) {
            final Method mainMethod = this.getMainMethod(loader, this.jar);

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

    private Method getMainMethod(final URLClassLoader loader, final Path jar) throws IOException {
        final URL manifestResource = loader.findResource("META-INF/MANIFEST.MF");

        final String mainClassName;
        try (final InputStream input = manifestResource.openStream()) {
            mainClassName = new Manifest(input).getMainAttributes().getValue("Main-Class");
        }

        final Class<?> mainClass;
        try {
            mainClass = Class.forName(mainClassName, true, loader);
        } catch (final ClassNotFoundException e) {
            throw new UnexpectedException("Failed to find main class in " + jar, e);
        }

        try {
            return mainClass.getMethod("main", String[].class);
        } catch (final NoSuchMethodException e) {
            throw new UnexpectedException("Could not find main method", e);
        }
    }
}
