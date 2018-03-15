package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.MethodInvocation;
import com.github.adamyork.elaborate.model.WriterMemo;
import com.github.adamyork.elaborate.service.PrinterService;
import com.github.adamyork.elaborate.service.WriterService;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

/**
 * Created by Adam York on 3/9/2018.
 * Copyright 2018
 */
public class Main {

    public static void main(final String[] args) throws IOException {
        final Options options = new Options();

        final Option inputOption = new Option("i", "input", true, "input file or folder.");
        inputOption.setRequired(true);
        options.addOption(inputOption);

        final Option classOption = new Option("c", "class", true, "class with entry method");
        classOption.setRequired(true);
        options.addOption(classOption);

        final Option methodOption = new Option("m", "method", true, "name of entry method");
        methodOption.setRequired(true);
        options.addOption(methodOption);

        final Option outputOption = new Option("o", "output", true, "output file");
        outputOption.setRequired(false);
        options.addOption(outputOption);

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

        final String inputPath = cmd.getOptionValue("input");
        final String className = cmd.getOptionValue("class");
        final String methodName = cmd.getOptionValue("method");

        final Elaborator elaborator = new Elaborator(inputPath, className, methodName);
        final List<MethodInvocation> methodInvocations = elaborator.run();

        final Optional<String> outputFilePath = Optional.ofNullable(cmd.getOptionValue("output"));
        if (outputFilePath.isPresent()) {
            final WriterService writerService = new WriterService(className, methodName, outputFilePath.get());
            writerService.write(methodInvocations, 0, new WriterMemo(""));
            System.exit(0);
        }

        final PrinterService printerService = new PrinterService(className, methodName);
        printerService.print(methodInvocations, 0);
        System.exit(0);
    }


}
