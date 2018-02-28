/*
 * MIT License
 *
 * Copyright (c) 2017 TU Wien
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.horndroid;

import java.nio.file.Paths;

import com.horndroid.analysis.Stubs;
import com.horndroid.exceptions.ReportWritingException;
import com.horndroid.executors.HorndroidExecutor;
import com.horndroid.model.Report;
import com.horndroid.printers.ReportPrinter;
import com.horndroid.printers.ReportWriterFactory;
import org.apache.commons.cli.*;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jf.util.ConsoleUtil;
import org.jf.util.SmaliHelpFormatter;

import java.io.File;
import java.util.List;

public class Main {
    public static final String COMMAND_EXPECTED = "java -jar fshorndroid-version.jar [options] '/' '%apktool%/' '<apk-file>'";
    public static final String APKTOOL_JAR = "apktool.jar";
    private static final org.apache.commons.cli.Options options;
    private static Logger LOGGER;
    private static Options hornDroidOptions = new Options();
    private static String APK_TOOL_DIR_PATH = "";
    private static String INPUT_APK_PATH = "";
    private static String OUTPUT_FILE_PATH = "OUTPUT.report";

    static {
        options = new org.apache.commons.cli.Options();
        options.addOption("q", false, "precise query results");
        options.addOption("w", false, "sensitive array indexes");
        options.addOption("t", false, "load stubs");
        options.addOption("n", true, "bitvector size (default 64)");
        options.addOption("i", false, "flow insensitive heap");
        options.addOption("r", true, "number of queries");
        options.addOption("d", true, "print debugging information (argument: integer 1 - taint information, 2 - localheap, or 3 - global heap");
        options.addOption("l", false, "stop after the first leak is found");
        options.addOption("s", false, "sensitive heap only for the objects created in the method that contains a call to a sink");
        options.addOption("g", false, "skip unknown methods");
        options.addOption("f", true, "filter classes starting from the init activity with a specified width (argument : integer, starting from 1" +
                "number of classes that should be included together with the init class to the analysis" +
                ")");
        options.addOption("u", false, "class filtering should be done sound, e.g., if a skipped method body contains a call to a sink it becomes a sink");
    }

    public static void main(String[] args) throws ReportWritingException {
	System.setProperty("logFilename", Paths.get(args[args.length-1]).getFileName().toString()); 
	LOGGER = LogManager.getLogger(Main.class);
        parseCommandLine(args);
        configuration();

        HorndroidExecutor horndroidExecutor;
        if (apkToolPathCorrect()) {
            horndroidExecutor = new HorndroidExecutor(hornDroidOptions, APK_TOOL_DIR_PATH, INPUT_APK_PATH);
        } else {
            LOGGER.info("Provided apktool.jar path is not correct, falling back to default");
            horndroidExecutor = new HorndroidExecutor(hornDroidOptions, INPUT_APK_PATH);
        }
        final List<Report> reports = horndroidExecutor.execute();
        display(reports);


    }

    private static void configuration() {
        if (!hornDroidOptions.nfsanalysis){
            LOGGER.info("Flow Sensitive Analysis on "+ hornDroidOptions.bitvectorSize + " bitvectors size");
        }else{
            LOGGER.info("Standard Analysis on "+ hornDroidOptions.bitvectorSize + " bitvectors size");
        }
        /*Stubs stubs = new Stubs(hornDroidOptions);
        if (hornDroidOptions.stubs) {
            long startTimeA = System.nanoTime();
            LOGGER.info("Loading Standard Java and Android libraries ...");
            stubs.process();
            long endTimeA = System.nanoTime();
            LOGGER.info("done in "
                    + Long.toString((endTimeA - startTimeA) / 1000000)
                    + " milliseconds");
        }
        else{
            System.out.println("Not Loading stubs!");
        }*/
    }

    private static void display(List<Report> reports) throws ReportWritingException {
        for (Report report : reports) {
            printReportToConsole(report);
            printReportToFile(report);
        }
    }

    private static boolean apkToolPathCorrect() {
        File apkTool = new File(APK_TOOL_DIR_PATH + APKTOOL_JAR);
        return apkTool.exists();
    }

    private static void parseCommandLine(String[] args) {
        LOGGER.info("Starting fsHornDroid...");
        CommandLineParser parser = new DefaultParser();
        CommandLine commandLine;
        try {
            commandLine = parser.parse(options, args);
        } catch (ParseException ex) {
            usage();
            return;
        }
        String[] otherArgs = getProgramArguments(commandLine);
        Option[] clOptions = commandLine.getOptions();
        getOptionsDirective(commandLine, clOptions);

        APK_TOOL_DIR_PATH = otherArgs[1];
        INPUT_APK_PATH = otherArgs[2];
        if(otherArgs.length==4){
            OUTPUT_FILE_PATH = otherArgs[3];
        }

        initializeOutputFile(otherArgs);
    }

    private static String[] getProgramArguments(CommandLine commandLine) {
        String[] otherArgs = commandLine.getArgs();
        if (otherArgs.length < 3) {
            usage();
            System.exit(0);
        }
        return otherArgs;
    }

    private static void getOptionsDirective(CommandLine commandLine, Option[] clOptions) {
        for (Option option : clOptions) {
            String opt = option.getOpt();
            switch (opt.charAt(0)) {
                case 'w':
                    hornDroidOptions.arrays = true;
                    break;
                case 'q':
                    hornDroidOptions.verboseResults = true;
                    break;
                case 'd':
                {
                    hornDroidOptions.debug = true;
                    String optionArg= commandLine.getOptionValue("d");
                    if (optionArg != null){
                        int dint = Integer.parseInt(optionArg);
                        hornDroidOptions.debugInt = dint;
                    }
                }
                break;
                case 't':
                    hornDroidOptions.stubs = true;
                    break;
                case 's':
                    hornDroidOptions.sensIfHasSink = true;
                    break;
                case 'n':
                    hornDroidOptions.bitvectorSize = Integer.parseInt(commandLine.getOptionValue("n"));
                    break;
                case 'o':
                    hornDroidOptions.timeout= Integer.parseInt(commandLine.getOptionValue("o"));
                    break;
                case 'r':
                    hornDroidOptions.maxQueries= Integer.parseInt(commandLine.getOptionValue("r"));
                    break;
                case 'l':
                    hornDroidOptions.tillFirstLeak = true;
                    break;
                case 'm':
                    hornDroidOptions.oldUnknown = true;
                    break;
                case 'i':
                    hornDroidOptions.nfsanalysis = true;
                    break;
                case 'p':
                    hornDroidOptions.pointersMerge = true;
                    break;
                case 'g':
                    hornDroidOptions.nopUnknown = true;
                    break;
                case 'f':
                    hornDroidOptions.filterClasses= Integer.parseInt(commandLine.getOptionValue("f"));
                    break;
                case 'u':
                    hornDroidOptions.filterClassesSound = true;
            }

        }
    }

    private static void initializeOutputFile(String[] otherArgs) {
        if (otherArgs.length == 4) {
            OUTPUT_FILE_PATH = otherArgs[3];
        }
    }

    private static void usage() {
        SmaliHelpFormatter formatter = new SmaliHelpFormatter();
        int consoleWidth = ConsoleUtil.getConsoleWidth();
        if (consoleWidth <= 0) {
            consoleWidth = 100;
        }
        formatter.setWidth(consoleWidth);
        System.out.println(COMMAND_EXPECTED);
        System.out.println("options:");
        System.out.println("-q precise query results");
        System.out.println("-w sensitive array indexes");
        System.out.println("-n bitvector size (default 64)");
        System.out.println("-i flow insensitive heap");
        System.out.println("-r number of queries");
        System.out.println("-d print debugging information (argument: integer 1 - taint information, 2 - localheap, or 3 - global heap");
        System.out.println("-l stop after the first leak is found");
        System.out.println("-s sensitive heap only for the objects created in the method that contains a call to a sink.");
    }

    private static void printReportToFile(Report report) throws ReportWritingException {
        ReportPrinter printer = ReportWriterFactory.getReportToJsonPrinter();
        printer.writeReportToFile(report, OUTPUT_FILE_PATH + "/" + report.getTag()+".json");
    }

    private static void printReportToConsole(Report report) throws ReportWritingException {
        ReportPrinter printer = ReportWriterFactory.getReportToJsonPrinter();
        printer.printReport(report);
    }
}
