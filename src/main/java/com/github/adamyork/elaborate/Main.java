package com.github.adamyork.elaborate;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

import java.io.IOException;

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

        final Engine engine = new Engine(inputPath, className, methodName, outputFilePath);
        System.exit(engine.run());
    }
}
