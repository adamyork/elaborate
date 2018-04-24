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
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.jooq.lambda.Unchecked;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
@SuppressWarnings("WeakerAccess")
public class Main {

    private static final Logger LOG = LogManager.getLogger(Main.class);

    public static void main(final String[] args) throws IOException {

        final Options options = new Options();
        final Option classOption = new Option("c", "config", true, "json configuration");
        final Option verboseOption = new Option("v", "verbose", false, "verbose logging level");
        classOption.setRequired(true);
        verboseOption.setRequired(false);
        options.addOption(classOption);
        options.addOption(verboseOption);

        final CommandLineParser parser = new DefaultParser();
        final HelpFormatter formatter = new HelpFormatter();

        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (final ParseException e) {
            LOG.error(e.getMessage());
            formatter.printHelp("elaborate", options);
            System.exit(1);
            return;
        }

        final String json = new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("config"))));
        final ObjectMapper mapper = new ObjectMapper();
        final Config config = mapper.readValue(json, Config.class);

        final Boolean verbose = cmd.hasOption("v");

        if (verbose) {
            LOG.info("verbose logging enabled");
            Configurator.setRootLevel(Level.DEBUG);
        }

        final String inputPath = config.getInput();
        final List<String> classNames = config.getEntryClass();
        final List<String> methodNames = config.getEntryMethod();
        final List<String> includes = config.getIncludes();
        final List<String> excludes = config.getExcludes();
        final List<String> implicitMethods = config.getImplicitMethods();
        final Optional<List<String>> maybeWhiteList = Optional.ofNullable(config.getWhiteList());
        final Optional<List<String>> outputFilePathOptional = Optional.ofNullable(config.getOutput());

        final int exitCode = IntStream.range(0, classNames.size()).map(index -> {

            final String className = classNames.get(index);
            final String methodName = methodNames.get(index);

            LOG.info("elaborating " + className + "::" + methodName);
            LOG.debug("config---------------------------------");
            LOG.debug("includes " + includes.toString());
            LOG.debug("excludes " + excludes.toString());
            LOG.debug("implicitMethods " + implicitMethods.toString());
            LOG.debug("maybeWhiteList " + maybeWhiteList.toString());
            LOG.debug("outputFilePathOptional " + outputFilePathOptional.toString());
            LOG.debug("---------------------------------------");

            final Elaborator elaborator = new Elaborator(inputPath, className, methodName,
                    includes, excludes, implicitMethods);
            final List<MethodInvocation> methodInvocations = elaborator.run();
            final WhiteListService whiteListService = new WhiteListService();
            LOG.info("applying filters");
            final List<MethodInvocation> maybeFiltered = whiteListService.manageList(methodInvocations, maybeWhiteList,
                    className, methodName);

            if (outputFilePathOptional.isPresent()) {
                final List<String> outputFilePath = outputFilePathOptional.get();
                if (outputFilePath.get(index).contains(".svg")) {
                    LOG.info("writing svg output");
                    final UmlService umlService = new UmlService(className, methodName, outputFilePath.get(index));
                    Unchecked.consumer(o -> umlService.write(maybeFiltered)).accept(null);
                    LOG.info("elaboration complete");
                    return 0;
                }
                LOG.info("writing text output");
                final TextService textService = new TextService();
                textService.write(className, methodName, outputFilePath.get(index), maybeFiltered, 0,
                        new WriterMemo.Builder("").build());
                LOG.info("elaboration complete");
                return 0;
            }

            final ConsoleService consoleService = new ConsoleService();
            consoleService.print(className, methodName, maybeFiltered, 0);
            LOG.info("elaboration complete");
            return 0;
        }).sum();
        System.exit(exitCode);
    }

}
