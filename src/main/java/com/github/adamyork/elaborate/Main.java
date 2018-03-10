package com.github.adamyork.elaborate;

import com.github.adamyork.elaborate.model.CallObject;
import com.github.adamyork.elaborate.service.PrinterService;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.util.List;

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
        outputOption.setRequired(true);
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
        final String outputFilePath = cmd.getOptionValue("output");

        final Elaborator elaborator = new Elaborator(inputPath, className, methodName, outputFilePath);
        final List<CallObject> callObjects = elaborator.run();

        final PrinterService printerService = new PrinterService(className, methodName);
        printerService.print(callObjects, 0);
        System.exit(0);
    }


}
