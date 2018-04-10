package com.github.adamyork.elaborate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.adamyork.elaborate.model.Config;
import com.github.adamyork.elaborate.model.MethodInvocation;
import com.github.adamyork.elaborate.model.WriterMemo;
import com.github.adamyork.elaborate.service.ConsoleService;
import com.github.adamyork.elaborate.service.TextService;
import com.github.adamyork.elaborate.service.UMLService;
import com.github.adamyork.elaborate.service.WhiteListService;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class Main {

    public static void main(final String[] args) throws IOException {

        final Options options = new Options();
        final Option classOption = new Option("c", "config", true, "class with entry method");
        classOption.setRequired(true);
        options.addOption(classOption);

        final CommandLineParser parser = new DefaultParser();
        final HelpFormatter formatter = new HelpFormatter();

        CommandLine cmd;
        try {
            cmd = parser.parse(options, args);
        } catch (final ParseException e) {
            System.out.println(e.getMessage());
            formatter.printHelp("elaborate", options);
            System.exit(1);
            return;
        }

        final String json = new String(Files.readAllBytes(Paths.get(cmd.getOptionValue("config"))));
        final ObjectMapper mapper = new ObjectMapper();
        final Config config = mapper.readValue(json, Config.class);

        final String inputPath = config.getInput();
        final String className = config.getEntryClass();
        final String methodName = config.getEntryMethod();
        final List<String> includes = config.getIncludes();
        final List<String> excludes = config.getExcludes();
        final List<String> implicitMethods = config.getImplicitMethods();
        final Optional<List<String>> maybeWhiteList = Optional.ofNullable(config.getWhiteList());

        final Elaborator elaborator = new Elaborator(inputPath, className, methodName,
                includes, excludes, implicitMethods);
        final List<MethodInvocation> methodInvocations = elaborator.run();
        final List<MethodInvocation> maybeFiltered = WhiteListService.manageList(methodInvocations, maybeWhiteList, className, methodName);

        final Optional<String> outputFilePathOptional = Optional.ofNullable(config.getOutput());
        if (outputFilePathOptional.isPresent()) {
            final String outputFilePath = outputFilePathOptional.get();
            if (outputFilePath.contains(".svg")) {
                final UMLService umlService = new UMLService(className, methodName, outputFilePath);
                umlService.write(maybeFiltered);
                System.exit(0);
            }
            final TextService textService = new TextService(className, methodName, outputFilePath);
            textService.write(maybeFiltered, 0, new WriterMemo.Builder("").build());
            System.exit(0);
        }

        final ConsoleService consoleService = new ConsoleService(className, methodName);
        consoleService.print(maybeFiltered, 0);
        System.exit(0);
    }

}
