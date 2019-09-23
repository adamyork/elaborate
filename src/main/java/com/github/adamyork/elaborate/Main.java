package com.github.adamyork.elaborate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.adamyork.elaborate.model.Config;
import com.github.adamyork.elaborate.model.MethodInvocation;
import com.github.adamyork.elaborate.model.WriterMemo;
import com.github.adamyork.elaborate.service.ConsoleService;
import com.github.adamyork.elaborate.service.TextService;
import com.github.adamyork.elaborate.service.UmlService;
import com.github.adamyork.elaborate.service.WhiteListService;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jooq.lambda.Unchecked;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(final String[] args) {
        System.exit(run(args));
    }

    private static int run(final String[] args) {
        final Options options = new Options();
        final Option classOption = new Option("c", "config", true, "json configuration");
        final Option verboseOption = new Option("v", "verbose", false, "verbose logging level");
        classOption.setRequired(true);
        verboseOption.setRequired(false);
        options.addOption(classOption);
        options.addOption(verboseOption);
        return processCommandLine(args, options)
                .map(Main::processConfiguration)
                .orElseGet(() -> handleCommandLineError(options));

    }

    private static Optional<CommandLine> processCommandLine(final String[] args, final Options options) {
        return Optional.ofNullable(Unchecked.supplier(() -> new DefaultParser().parse(options, args)).get());
    }

    private static int processConfiguration(final CommandLine commandLine) {
        return readConfig(commandLine).map(config -> parseConfig(config, commandLine)).orElse(1);
    }

    private static int handleCommandLineError(final Options options) {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("elaborate", options);
        return 1;
    }

    private static Optional<Config> readConfig(final CommandLine commandLine) {
        final String json = Optional.ofNullable(Unchecked.supplier(() -> new String(Files.readAllBytes(Paths.get(commandLine.getOptionValue("config"))))).get())
                .orElse("");
        return Optional.ofNullable(Unchecked.supplier(() -> new ObjectMapper().readValue(json, Config.class)).get());
    }

    private static int parseConfig(final Config config, final CommandLine commandLine) {
        final String verbose = Optional.of(commandLine.hasOption("v"))
                .filter(bool -> bool)
                .map(bool -> {
                    Configurator.setRootLevel(Level.DEBUG);
                    return "enabled";
                }).orElse("disabled");

        LOG.info("verbose logging " + verbose);

        final String inputPath = config.getInput();
        final List<String> classNames = config.getEntryClass();
        final List<String> methodNames = config.getEntryMethod();
        final List<String> includes = config.getIncludes();
        final List<String> excludes = config.getExcludes();
        final List<String> implicitMethods = config.getImplicitMethods();
        final Optional<List<String>> maybeWhiteList = Optional.ofNullable(config.getWhiteList());
        final Optional<List<String>> maybeOutputFilePath = Optional.ofNullable(config.getOutput());
        final Optional<List<String>> maybeEntryMethodArgs = Optional.ofNullable(config.getEntryMethodArgs());

        return IntStream.range(0, classNames.size())
                .parallel()
                .map(index -> elaborate(index, inputPath, classNames, methodNames, includes, excludes,
                        implicitMethods, maybeWhiteList, maybeOutputFilePath, maybeEntryMethodArgs))
                .sum();
    }

    @SuppressWarnings("OptionalUsedAsFieldOrParameterType")
    private static int elaborate(final int index,
                                 final String inputPath,
                                 final List<String> classNames,
                                 final List<String> methodNames,
                                 final List<String> includes,
                                 final List<String> excludes,
                                 final List<String> implicitMethods,
                                 final Optional<List<String>> maybeWhiteList,
                                 final Optional<List<String>> maybeOutputFilePath,
                                 final Optional<List<String>> maybeEntryMethodArgs) {
        final String className = classNames.get(index);
        final String methodName = methodNames.get(index);

        LOG.info("elaborating " + className + "::" + methodName);
        LOG.debug("config---------------------------------");
        LOG.debug("includes " + includes.toString());
        LOG.debug("excludes " + excludes.toString());
        LOG.debug("implicitMethods " + implicitMethods.toString());
        LOG.debug("maybeWhiteList " + maybeWhiteList.toString());
        LOG.debug("maybeOutputFilePath " + maybeOutputFilePath.toString());
        LOG.debug("maybeEntryMethodArgs " + maybeEntryMethodArgs.toString());
        LOG.debug("---------------------------------------");

        final String entryMethodArgs = maybeEntryMethodArgs.map(strings -> strings.get(index)).orElse("");

        final Elaborator elaborator = new Elaborator(inputPath, className, methodName, entryMethodArgs,
                includes, excludes, implicitMethods);
        final List<MethodInvocation> methodInvocations = elaborator.run();
        final WhiteListService whiteListService = new WhiteListService();

        LOG.info("applying filters");

        final List<MethodInvocation> maybeFiltered = whiteListService.manageList(methodInvocations, maybeWhiteList,
                className, methodName);
        return maybeOutputFilePath
                .map(outputFilePath -> Optional.of(outputFilePath.get(index).contains(".svg"))
                        .filter(bool -> bool)
                        .map(bool -> elaborateToUml(index, className, methodName, outputFilePath, maybeFiltered))
                        .orElseGet(() -> elaborateToText(index, className, methodName, outputFilePath, maybeFiltered)))
                .orElseGet(() -> elaborateToConsole(className, methodName, maybeFiltered));
    }

    private static int elaborateToUml(final int index,
                                      final String className,
                                      final String methodName,
                                      final List<String> outputFilePath,
                                      final List<MethodInvocation> maybeFiltered) {
        LOG.info("writing svg output");
        final UmlService umlService = new UmlService(className, methodName, outputFilePath.get(index));
        final int fileSize = Unchecked.supplier(() -> umlService.write(maybeFiltered)).get();
        LOG.info("elaboration complete " + fileSize + " created.");
        return 0;
    }

    private static int elaborateToText(final int index,
                                       final String className,
                                       final String methodName,
                                       final List<String> outputFilePath,
                                       final List<MethodInvocation> maybeFiltered) {
        LOG.info("writing text output");
        final TextService textService = new TextService();
        final int lines = textService.write(className, methodName, outputFilePath.get(index), maybeFiltered, 0,
                new WriterMemo.Builder("").build());
        LOG.info("elaboration complete " + lines + " written.");
        return 0;
    }

    private static int elaborateToConsole(final String className,
                                          final String methodName,
                                          final List<MethodInvocation> maybeFiltered) {
        final ConsoleService consoleService = new ConsoleService();
        final int lines = consoleService.print(className, methodName, maybeFiltered, 0);
        LOG.info("elaboration complete " + lines + " written.");
        return 0;
    }

}
