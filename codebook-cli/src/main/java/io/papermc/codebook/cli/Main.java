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

package io.papermc.codebook.cli;

import dev.denwav.hypo.core.HypoConfig;
import io.papermc.codebook.CodeBook;
import io.papermc.codebook.config.CodeBookContext;
import io.papermc.codebook.config.CodeBookCoordsResource;
import io.papermc.codebook.config.CodeBookFileResource;
import io.papermc.codebook.config.CodeBookInput;
import io.papermc.codebook.config.CodeBookJarInput;
import io.papermc.codebook.config.CodeBookResource;
import io.papermc.codebook.config.CodeBookUriResource;
import io.papermc.codebook.config.CodeBookVersionInput;
import io.papermc.codebook.exceptions.UserErrorException;
import io.papermc.codebook.report.ReportType;
import io.papermc.codebook.report.Reports;
import io.papermc.codebook.util.Downloader;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.function.Function;
import java.util.zip.ZipFile;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.bridge.SLF4JBridgeHandler;
import picocli.CommandLine;
import uk.org.lidalia.sysoutslf4j.context.SysOutOverSLF4J;

@CommandLine.Command(
        name = "codebook",
        versionProvider = VersionProvider.class,
        mixinStandardHelpOptions = true,
        descriptionHeading = "%n",
        parameterListHeading = "%n",
        optionListHeading = "%nOptions:%n",
        showAtFileInUsageHelp = true,
        description = "Applies PaperMC's remap process to an input jar.",
        sortOptions = false,
        usageHelpAutoWidth = true)
public final class Main implements Callable<Integer> {

    @CommandLine.ArgGroup(multiplicity = "1", exclusive = false)
    private @Nullable ReportOptions reports;

    static final class ReportOptions {

        @CommandLine.Option(
                names = "--reports-dir",
                paramLabel = "<reports-dir>",
                description = "Parent directory to output any generated reports",
                hidden = true)
        private @Nullable Path reportsDir;

        @CommandLine.ArgGroup(multiplicity = "1", exclusive = false)
        private SelectedReports selectedReports;

        static final class SelectedReports {

            @CommandLine.Option(
                    names = "--report",
                    paramLabel = "<report>",
                    description = "Set of report types to generate",
                    hidden = true)
            private Set<ReportType> reports;

            @CommandLine.Option(
                    names = "--all-reports",
                    paramLabel = "<all-reports>",
                    description = "Generate all reports",
                    hidden = true)
            private boolean allReports;
        }
    }

    @CommandLine.ArgGroup(
            multiplicity = "1",
            heading = "%n%nThe remapper must be an executable tiny-remapper jar. "
                    + "This is the 'fat' classifier when downloading from Maven. It can be provided several different ways, the simplest being to just "
                    + "specify the Maven coordinates (with no classifier).%n")
    private RemapperOptions remapper;

    static final class RemapperOptions {
        @CommandLine.Option(
                names = {"-r", "--remapper-coords"},
                paramLabel = "<art-coords>",
                description =
                        "The Maven coordinates for the executable AutoRenamingTool jar to use for the remapping process.")
        private @Nullable String remapperCoords;

        @CommandLine.Option(
                names = {"--remapper-file"},
                paramLabel = "<art-file>",
                description = "The executable AutoRenamingTool jar to use for the remapping process.")
        private @Nullable Path remapperFile;

        @CommandLine.Option(
                names = "--remapper-uri",
                paramLabel = "<art-uri>",
                description =
                        "A download URL for the executable AutoRenamingTool jar to use for the remapping process.")
        private @Nullable URI remapperUri;
    }

    @CommandLine.ArgGroup(
            heading =
                    "%n%nMappings are required when not using the --mc-version option to automatically download a version. "
                            + "They can still be optionally provided using one of the 2 mappings options below, "
                            + "but one of either --mappings-file or --mappings-uri is required when using --input to manually specify a jar file.%n")
    private @Nullable MappingsOptions mappings;

    static final class MappingsOptions {
        @CommandLine.Option(
                names = {"-m", "--mappings-file"},
                paramLabel = "<mappings-file>",
                description = "The ProGuard mojmap mappings to use for base remapping.")
        private @Nullable Path mappingsFile;

        @CommandLine.Option(
                names = "--mappings-uri",
                paramLabel = "<mappings-uri>",
                description = "A download URL for the ProGuard mojmap mappings to use for base remapping.")
        private @Nullable URI mappingsUri;
    }

    @CommandLine.ArgGroup(
            heading = "%n%nParameter mappings are always optional, and can be specified several different ways.%n")
    private @Nullable ParamMappingsOptions paramMappings;

    static final class ParamMappingsOptions {
        @CommandLine.Option(
                names = {"-p", "--params-coords"},
                paramLabel = "<param-mappings-coords>",
                description =
                        "The Maven coordinates for TinyV2 mappings to use for parameter remapping. This is the preferred option, as it allows omitting other details.")
        private @Nullable String paramsCoords;

        @CommandLine.Option(
                names = {"--params-file"},
                paramLabel = "<param-mappings-file>",
                description = "The TinyV2 mappings to use for parameter remapping.")
        private @Nullable Path paramsFile;

        @CommandLine.Option(
                names = "--params-uri",
                paramLabel = "<param-mappings-uri>",
                description = "A download URL for the TinyV2 mappings to use for parameter remapping.")
        private @Nullable URI paramsUri;
    }

    @CommandLine.ArgGroup(exclusive = false)
    private @Nullable UnpickOptions unpick;

    static final class UnpickOptions {
        @CommandLine.ArgGroup(
                heading =
                        "%n%nUnpick requires unpick definitions. When specifying unpick definitions, unpick constants are also required.%n",
                multiplicity = "1")
        private @Nullable UnpickDefinitionsOptions unpickDefinitions;

        static final class UnpickDefinitionsOptions {
            @CommandLine.Option(
                    names = "--unpick-coords",
                    paramLabel = "<unpick-coords>",
                    description = "The Maven coordinates for the unpick definitions to use for the unpick process.")
            private @Nullable String unpickCoords;

            @CommandLine.Option(
                    names = {"--unpick-file"},
                    paramLabel = "<unpick-jar-file>",
                    description = "The unpick definitions file to use for the unpick process.")
            private @Nullable Path unpickFile;

            @CommandLine.Option(
                    names = "--unpick-uri",
                    paramLabel = "<unpick-uri>",
                    description = "A download URL for the unpick definitions to use for the unpick process.")
            private @Nullable URI unpickUri;
        }

        @CommandLine.ArgGroup(
                heading =
                        "%n%nUnpick requires a constants jar.  When specifying unpick constants, unpick definitions are also required.%n",
                multiplicity = "1")
        private @Nullable ConstantsJarOptions constantsJar;

        static final class ConstantsJarOptions {
            @CommandLine.Option(
                    names = "--constants-coords",
                    paramLabel = "<constants-coords>",
                    description = "The Maven coordinates for the constants jar to use for the unpick process.")
            private @Nullable String constantsCoords;

            @CommandLine.Option(
                    names = {"--constants-file"},
                    paramLabel = "<constants-jar-file>",
                    description = "The constants jar to use for the unpick process.")
            private @Nullable Path constantsFile;

            @CommandLine.Option(
                    names = "--constants-uri",
                    paramLabel = "<constants-uri>",
                    description = "A download URL for the constants jar to use for the unpick process.")
            private @Nullable URI constantsUri;
        }
    }

    @CommandLine.Option(
            names = {"-o", "--output"},
            required = true,
            paramLabel = "<output-jar>",
            description = "The jar file to write to. Will only overwrite an existing jar if -f or --force is provided.")
    private Path outputJar;

    @CommandLine.Option(
            names = {"-f", "--force"},
            description = "Set this flag to allow overwriting the output jar if it already exists.")
    private boolean forceWrite;

    @CommandLine.ArgGroup(
            multiplicity = "1",
            heading =
                    "%nThere are 2 methods of providing inputs, and they are mutually exclusive:"
                            + "%n  1. Simply specifying the desired --mc-version to download."
                            + "%n  2. By manually specifying the input files with --input. "
                            + "When using --input you can also provide additional jars for the classpath using --input-classpath.%n%n")
    private InputOptions inputs;

    static final class InputOptions {
        @CommandLine.Option(
                names = {"-x", "--mc-version"},
                required = true,
                paramLabel = "<version>",
                description = "The Minecraft version (matched from the manifest) to download and remap.")
        private @Nullable String mcVersion;

        @CommandLine.ArgGroup(exclusive = false, multiplicity = "1")
        private @Nullable InputFileOptions inputFile;
    }

    static class InputFileOptions {
        @CommandLine.Option(
                names = {"-i", "--input"},
                required = true,
                paramLabel = "<input-jar>",
                description = "The input jar to remap.")
        private Path inputJar;

        @CommandLine.Option(
                names = {"-c", "--input-classpath"},
                split = ":(?!\\\\)",
                splitSynopsisLabel = ":",
                paramLabel = "<jar>",
                description =
                        "Additional classpath jars, provided in standard classpath format (use : to separate jars on the path).")
        private @Nullable List<Path> inputClasspath;
    }

    @CommandLine.Option(
            names = "--mappings-maven-base-url",
            paramLabel = "url",
            description = "Provide a different Maven URL to resolve parameter mapping Maven coordinates. "
                    + "It should be the base URL so the Maven artifact path can be appended to it. "
                    + "The default value when not provided is ${DEFAULT-VALUE}.",
            defaultValue = Downloader.PARCHMENT_MAVEN)
    private String paramsMavenBaseUrl;

    @CommandLine.Option(
            names = "--remapper-maven-base-url",
            paramLabel = "url",
            description = "Provide a different Maven URL to resolve remapper Maven coordinates. "
                    + "It should be the base URL so the Maven artifact path can be appended to it. "
                    + "The default value when not provided is ${DEFAULT-VALUE}.",
            defaultValue = Downloader.NEO_MAVEN)
    private String remapperMavenBaseUrl;

    @CommandLine.Option(
            names = "--unpick-maven-base-url",
            paramLabel = "url",
            description = "Provide a different Maven URL to resolve unpick Maven coordinates. "
                    + "It should be the base URL so the Maven artifact path can be appended to it. "
                    + "There is no default value when not provided.")
    private @Nullable String unpickMavenBaseUrl;

    @CommandLine.Option(
            names = {"-v", "--verbose"},
            description = "Don't suppress logging.",
            defaultValue = "false")
    private boolean verbose;

    @CommandLine.Option(
            names = {"--temp-dir"},
            paramLabel = "<temp-dir>",
            description = "The temp dir to work in.")
    private @Nullable Path tempDir;

    @CommandLine.Option(
            names = {"--hypo-parallelism"},
            paramLabel = "<parallelism-level>",
            defaultValue = "-1",
            description = "The parallelism level to use for Hypo executions.")
    private int hypoConcurrency;

    public Main() {}

    public static void main(final String[] args) {
        final int exitCode = new CommandLine(new Main())
                .setExecutionExceptionHandler(new Main.SimpleExceptionHandler())
                .execute(args);
        System.exit(exitCode);
    }

    static class SimpleExceptionHandler implements CommandLine.IExecutionExceptionHandler {
        @Override
        public int handleExecutionException(
                final Exception ex, final CommandLine cmd, final CommandLine.ParseResult parseResult) throws Exception {
            if (!(ex instanceof UserErrorException)) {
                throw ex;
            }

            cmd.getErr().println(cmd.getColorScheme().errorText(ex.getMessage()));
            Throwable t = ex;
            int depth = 0;
            while (t.getCause() != null) {
                depth++;
                t = t.getCause();
                cmd.getErr().println(cmd.getColorScheme().errorText("    ".repeat(depth) + t.getMessage()));
            }

            return cmd.getExitCodeExceptionMapper() != null
                    ? cmd.getExitCodeExceptionMapper().getExitCode(ex)
                    : cmd.getCommandSpec().exitCodeOnExecutionException();
        }
    }

    @Override
    public Integer call() {
        final boolean v = this.verbose;
        if (!v) {
            SysOutOverSLF4J.sendSystemOutAndErrToSLF4J();
            SLF4JBridgeHandler.removeHandlersForRootLogger();
            SLF4JBridgeHandler.install();
        }

        try {
            final CodeBookContext context = this.createContext();
            new CodeBook(context).exec();
        } finally {
            if (!v) {
                SysOutOverSLF4J.stopSendingSystemOutAndErrToSLF4J();
            }
        }
        return 0;
    }

    private CodeBookContext createContext() {
        if (this.remapper.remapperFile != null) {
            this.verifyJarFile("Remapper", this.remapper.remapperFile);
        }

        if (this.inputs.inputFile != null) {
            this.verifyJarFile("Input", this.inputs.inputFile.inputJar);

            if (this.inputs.inputFile.inputClasspath != null) {
                for (final Path classpathJar : this.inputs.inputFile.inputClasspath) {
                    this.verifyJarFile("Classpath", classpathJar);
                }
            }
        }

        if (this.mappings == null && this.inputs.mcVersion == null) {
            throw new UserErrorException("No base mappings file was provided, and no MC version was provided. "
                    + "When not specifying an MC version, the base mappings file must be manually specified.");
        }
        if (this.mappings != null && this.mappings.mappingsFile != null) {
            this.verifyFileExists("Mappings file", this.mappings.mappingsFile);
        }

        if (this.paramMappings != null && this.paramMappings.paramsFile != null) {
            this.verifyFileExists("Param mappings file", this.paramMappings.paramsFile);
        }

        if (Files.isRegularFile(this.outputJar) && !this.forceWrite) {
            throw new UserErrorException(
                    "Output jar file exists, will not overwrite because --force option was not provided: "
                            + this.outputJar);
        }

        final CodeBookInput input;
        if (this.inputs.mcVersion != null) {
            input = new CodeBookVersionInput(this.inputs.mcVersion);
        } else {
            final var classpath = this.inputs.inputFile.inputClasspath == null
                    ? List.<Path>of()
                    : this.inputs.inputFile.inputClasspath;
            input = new CodeBookJarInput(this.inputs.inputFile.inputJar, classpath);
        }

        final @Nullable CodeBookResource remapper = this.getResource(
                "AutoRenamingTool.jar",
                this.remapper,
                r -> r.remapperFile,
                r -> r.remapperUri,
                r -> new Coords(r.remapperCoords, "all", null, this.remapperMavenBaseUrl));
        if (remapper == null) {
            throw new UserErrorException("No remapper provided");
        }

        final @Nullable CodeBookResource mappings =
                this.getResource("server_mappings.txt", this.mappings, m -> m.mappingsFile, m -> m.mappingsUri, null);

        final @Nullable CodeBookResource paramMappings = this.getResource(
                "parchment.zip",
                this.paramMappings,
                p -> p.paramsFile,
                p -> p.paramsUri,
                p -> new Coords(p.paramsCoords, null, "zip", this.paramsMavenBaseUrl));

        final @Nullable CodeBookResource unpickDefinitions = this.getResource(
                "unpick_definitions.jar",
                this.unpick != null ? this.unpick.unpickDefinitions : null,
                d -> d.unpickFile,
                d -> d.unpickUri,
                d -> {
                    if (this.unpickMavenBaseUrl == null) {
                        throw new UserErrorException(
                                "Cannot define unpick definitions Maven coordinates without also setting --unpick-maven-base-url");
                    }
                    return new Coords(d.unpickCoords, "constants", null, this.unpickMavenBaseUrl);
                });

        final @Nullable CodeBookResource constantJar = this.getResource(
                "unpick_constants.jar",
                this.unpick != null ? this.unpick.constantsJar : null,
                c -> c.constantsFile,
                c -> c.constantsUri,
                c -> {
                    if (this.unpickMavenBaseUrl == null) {
                        throw new UserErrorException(
                                "Cannot define unpick constants Maven coordinates without also setting --unpick-maven-base-url");
                    }
                    return new Coords(c.constantsCoords, "constants", null, this.unpickMavenBaseUrl);
                });

        @Nullable Reports reports = null;
        if (this.reports != null && this.reports.reportsDir != null) {
            final Set<ReportType> reportsToGenerate;
            if (this.reports.selectedReports.allReports) {
                reportsToGenerate = Set.of(ReportType.values());
            } else {
                reportsToGenerate = this.reports.selectedReports.reports;
            }
            reports = new Reports(this.reports.reportsDir, reportsToGenerate);
        }

        @Nullable HypoConfig hypoConfig = null;
        if (this.hypoConcurrency != -1) {
            hypoConfig =
                    HypoConfig.builder().withParallelism(this.hypoConcurrency).build();
        }

        return CodeBookContext.builder()
                .tempDir(this.tempDir)
                .remapperJar(remapper)
                .mappings(mappings)
                .paramMappings(paramMappings)
                .unpickDefinitions(unpickDefinitions)
                .constantsJar(constantJar)
                .outputJar(this.outputJar)
                .overwrite(this.forceWrite)
                .input(input)
                .reports(reports)
                .hypoConfig(hypoConfig)
                .build();
    }

    private <T> @Nullable CodeBookResource getResource(
            final String name,
            final @Nullable T resource,
            final Function<T, @Nullable Path> resourceFile,
            final Function<T, @Nullable URI> resourceUri,
            final @Nullable Function<T, Coords> resourceCoords) {
        if (resource == null) {
            return null;
        }

        final @Nullable Path file = resourceFile.apply(resource);
        if (file != null) {
            return new CodeBookFileResource(file);
        }

        final @Nullable URI uri = resourceUri.apply(resource);
        if (uri != null) {
            return new CodeBookUriResource(name, uri, null);
        }

        if (resourceCoords != null) {
            final Coords coords = resourceCoords.apply(resource);
            if (coords.coords != null) {
                return new CodeBookCoordsResource(coords.coords, coords.classifier, coords.extension, coords.baseUrl);
            }
        }

        throw new UserErrorException("No valid mappings configuration found (this is probably a bug)");
    }

    private record Coords(
            @Nullable String coords, @Nullable String classifier, @Nullable String extension, String baseUrl) {}

    private void verifyFileExists(final String name, final Path file) {
        if (!Files.isRegularFile(file)) {
            throw new UserErrorException(name + " is not a valid file: " + file);
        }
    }

    private void verifyJarFile(final String name, final Path jarFile) {
        this.verifyFileExists(name + " jar", jarFile);

        //noinspection EmptyTryBlock
        try (final ZipFile ignored = new ZipFile(jarFile.toFile())) {
        } catch (final IOException e) {
            throw new UserErrorException(name + " jar is not a valid jar file: " + jarFile, e);
        }
    }
}
