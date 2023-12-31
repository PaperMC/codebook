codebook
========

This is a small application / library for easily sharing jar remap logic between different Paper projects. It contains
the library itself, as well as a main class for use as a standalone CLI.

```
Usage: codebook [-fhV] [--mappings-maven-base-url=url] -o=<output-jar> [--remapper-maven-base-url=url] [--unpick-maven-base-url=url]
                (-r=<art-coords> | --remapper-file=<art-file> | --remapper-uri=<art-uri>) [-m=<mappings-file> |
                --mappings-uri=<mappings-uri>] [-p=<param-mappings-coords> | --params-file=<param-mappings-file> |
                --params-uri=<param-mappings-uri>] [[--unpick-coords=<unpick-coords> | --unpick-file=<unpick-jar-file> |
                --unpick-uri=<unpick-uri>] [--constants-coords=<constants-coords> | --constants-file=<constants-jar-file> |
                --constants-uri=<constants-uri>]] (-x=<version> | (-i=<input-jar> [-c=<jar>[:<jar>...]]...)) [@<filename>...]

Applies PaperMC's remap process to an input jar.

      [@<filename>...]      One or more argument files containing options.

Options:
  -o, --output=<output-jar> The jar file to write to. Will only overwrite an existing jar if -f or --force is provided.
  -f, --force               Set this flag to allow overwriting the output jar if it already exists.
      --mappings-maven-base-url=url
                            Provide a different Maven URL to resolve parameter mapping Maven coordinates. It should be the base URL so
                              the Maven artifact path can be appended to it. The default value when not provided is https://maven.
                              parchmentmc.org/.
      --remapper-maven-base-url=url
                            Provide a different Maven URL to resolve remapper Maven coordinates. It should be the base URL so the Maven
                              artifact path can be appended to it. The default value when not provided is https://maven.neoforged.
                              net/releases/.
      --unpick-maven-base-url=url
                            Provide a different Maven URL to resolve unpick Maven coordinates. It should be the base URL so the Maven
                              artifact path can be appended to it. There is no default value when not provided.
  -h, --help                Show this help message and exit.
  -V, --version             Print version information and exit.


The remapper must be an executable tiny-remapper jar. This is the 'fat' classifier when downloading from Maven. It can be provided
several different ways, the simplest being to just specify the Maven coordinates (with no classifier).
  -r, --remapper-coords=<art-coords>
                            The Maven coordinates for the executable AutoRenamingTool jar to use for the remapping process.
      --remapper-file=<art-file>
                            The executable AutoRenamingTool jar to use for the remapping process.
      --remapper-uri=<art-uri>
                            A download URL for the executable AutoRenamingTool jar to use for the remapping process.


Mappings are required when not using the --mc-version option to automatically download a version. They can still be optionally provided
using one of the 2 mappings options below, but one of either --mappings-file or --mappings-uri is required when using --input to manually
specify a jar file.
  -m, --mappings-file=<mappings-file>
                            The ProGuard mojmap mappings to use for base remapping.
      --mappings-uri=<mappings-uri>
                            A download URL for the ProGuard mojmap mappings to use for base remapping.


Parameter mappings are always optional, and can be specified several different ways.
  -p, --params-coords=<param-mappings-coords>
                            The Maven coordinates for TinyV2 mappings to use for parameter remapping. This is the preferred option, as it
                              allows omitting other details.
      --params-file=<param-mappings-file>
                            The TinyV2 mappings to use for parameter remapping.
      --params-uri=<param-mappings-uri>
                            A download URL for the TinyV2 mappings to use for parameter remapping.


Unpick requires unpick definitions. When specifying unpick definitions unpick constants are also required.
      --unpick-coords=<unpick-coords>
                            The Maven coordinates for the unpick definitions to use for the unpick process.
      --unpick-file=<unpick-jar-file>
                            The unpick definitions file to use for the unpick process.
      --unpick-uri=<unpick-uri>
                            A download URL for the unpick definitions to use for the unpick process.


Unpick requires a constants jar. This is optional when specifying the parameter mappings using Maven coordinates (preferred). If not
using Maven coordinates for parameter mappings, then this is required for unpick to run, otherwise it will be skipped.
      --constants-coords=<constants-coords>
                            The Maven coordinates for the constants jar to use for the unpick process.
      --constants-file=<constants-jar-file>
                            The constants jar to use for the unpick process.
      --constants-uri=<constants-uri>
                            A download URL for the constants jar to use for the unpick process.

There are 2 methods of providing inputs, and they are mutually exclusive:
  1. Simply specifying the desired --mc-version to download.
  2. By manually specifying the input files with --input. When using --input you can also provide additional jars for the classpath using
--input-classpath.

  -x, --mc-version=<version>
                            The Minecraft version (matched from the manifest) to download and remap.
  -i, --input=<input-jar>   The input jar to remap.
  -c, --input-classpath=<jar>[:<jar>...]
                            Additional classpath jars, provided in standard classpath format (use : to separate jars on the path).
```

Building
========

Java 17 is required.

```sh
./gradlew build
```

Format source code (to make Spotless and others happy):
```sh
./gradlew format
```

License
=======

[LGPL-3.0-only](license.txt)
