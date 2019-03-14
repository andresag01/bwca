package com.bwca.driver;

import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.LinkedList;
import java.util.List;

import com.bwca.models.Model;
import com.bwca.models.WCETModel;
//import com.bwca.models.WCAModel;
//import com.bwca.models.WCMAModel;
import com.bwca.cfg.ISAModule;
import com.bwca.cfg.CFGConfiguration;
import com.bwca.utils.PlatformUtils;

public class Controller
{
    static final String READELF = "arm-none-eabi-readelf";
    static final String OBJDUMP = "arm-none-eabi-objdump";

    static final String[] READELF_CMD = {
        READELF,
        "-s",
    };
    static final String[] OBJDUMP_CMD = {
        OBJDUMP,
        "-C",
        "-d",
    };

    // Command line options
    private String outputDir;
    private String binFile;
    private String configFile;
    private int fetchWidthBytes;
    private Set<String> selectedModels;
    private List<Model> models;
    private CFGConfiguration cfgConfig;
    private String entryFunctionName;
    private String mallocFunctionName;
    private String callocFunctionName;
    private String reallocFunctionName;

    public static final String[][] MODELS = {
        { "wcet", "Worst-Case Execution Time" },
        { "wca", "Worst-Case Allocation" },
        { "wcma", "Worst-Case Memory Access" },
    };
    private static final String HELP_MSG = "Bristol Worst Case Analysis Tool\n"
        + "\n"
        + "ARGUMENTS:\n"
        + "    -b       Binary file to analyze.\n"
        + "    -o       Directory to store output files.\n"
        + "    -h       Prints this help message\n"
        + "    -l       Print a list of options for -m\n"
        + "    -w       Fetch width in bytes. Default: 4\n"
        + "    -e       Entry function\n"
        + "    -m       Analyze the binary file with the specified model.\n"
        + "             Repeat this option as many times as needed to apply \n"
        + "             more than one model. Run the program with -l to view\n"
        + "             a list of options.\n"
        + "    -malloc  Name of malloc function. Default: malloc\n"
        + "    -calloc  Name of calloc function. Default: calloc\n"
        + "    -realloc Name of realloc function. Default: realloc\n"
        + "    -c       CFG Configuration file.\n";

    public static void main(String[] args)
    {
        Controller controller = new Controller();
        controller.parseCmdLineArguments(args);
        controller.analyze();
    }

    public Controller()
    {
        outputDir = null;
        binFile = null;
        configFile = null;
        selectedModels = new HashSet<String>();
        models = new LinkedList<Model>();
        fetchWidthBytes = 4;
        cfgConfig = new CFGConfiguration();
        entryFunctionName = null;
        mallocFunctionName = "malloc";
        callocFunctionName = "calloc";
        reallocFunctionName = "realloc";
    }

    private void parseCmdLineArguments(String[] args)
    {
        boolean fail = false;

        for (int i = 0; i < args.length; i++)
        {
            switch (args[i])
            {
                case "-b":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-b option takes one argument");
                        System.exit(1);
                    }
                    binFile = args[++i];
                    break;

                case "-o":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-o option takes one argument");
                        System.exit(1);
                    }
                    outputDir = args[++i];
                    break;

                case "-h":
                    System.out.println(HELP_MSG);
                    System.exit(0);
                    break;

                case "-m":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-m option takes one argument");
                        System.exit(1);
                    }
                    selectedModels.add(args[++i]);
                    break;

                case "-w":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-w option takes one argument");
                        System.exit(1);
                    }
                    fetchWidthBytes = Integer.parseInt(args[++i]);
                    break;

                case "-l":
                    StringBuilder builder = new StringBuilder();
                    for (String[] model : MODELS)
                    {
                        builder.append(String.format(
                            "    %6s  %s\n", model[0], model[1]));
                    }
                    System.out.println("Available models:");
                    System.out.print(builder.toString());
                    System.exit(0);
                    break;

                case "-c":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-c option takes one argument");
                        System.exit(1);
                    }
                    configFile = args[++i];
                    break;

                case "-e":
                    if (i + 1 == args.length)
                    {
                        System.out.println("-e option takes one argument");
                        System.exit(1);
                    }
                    entryFunctionName = args[++i];
                    break;

                case "-malloc":
                    if (i + 1 == args.length)
                    {
                        System.out.println(
                            "-malloc option takes one argument");
                        System.exit(1);
                    }
                    mallocFunctionName = args[++i];
                    break;

                case "-calloc":
                    if (i + 1 == args.length)
                    {
                        System.out.println(
                            "-calloc option takes one argument");
                        System.exit(1);
                    }
                    callocFunctionName = args[++i];
                    break;

                case "-realloc":
                    if (i + 1 == args.length)
                    {
                        System.out.println(
                            "-realloc option takes one argument");
                        System.exit(1);
                    }
                    reallocFunctionName = args[++i];
                    break;

                default:
                    System.out.println("Unrecognized option " + args[i]);
                    System.exit(1);
            }
        }

        // Check for errors
        if (outputDir == null)
        {
            fail = true;
            System.out.println("Missing output directory");
        }
        if (binFile == null)
        {
            fail = true;
            System.out.println("Missing binary file");
        }
        if (selectedModels.isEmpty())
        {
            fail = true;
            System.out.println("Must select at least one model");
        }
        if (entryFunctionName == null)
        {
            fail = true;
            System.out.println("Missing entry function");
        }
        for (String modelOption : selectedModels)
        {
            switch (modelOption)
            {
                case "wcet":
                    models.add(new WCETModel());
                    break;

                //case "wca":
                //    models.add(new WCAModel(mallocFunctionName,
                //                            callocFunctionName,
                //                            reallocFunctionName));
                //    break;

                //case "wcma":
                //    models.add(new WCMAModel(fetchWidthBytes));
                //    break;

                default:
                    System.out.println("Unrecognized model " + modelOption);
                    fail = true;
                    break;
            }
        }
        if (configFile != null)
        {
            cfgConfig.loadFile(configFile);
        }

        if (fail)
        {
            System.exit(1);
        }
    }

    private void analyze()
    {
        ArrayList<String> objdump = null;
        ArrayList<String> readelf = null;

        // Create output directory (if it does not already exist)
        PlatformUtils.createOutputDirectory(outputDir);

        // Run objdump and readelf, store output in a file and then read it
        // into memory
        try
        {
            System.out.println("Running objdump");
            File outputObjdumpFile =
                new File(outputDir + File.separator + "objdump.log");
            String[] cmd = Arrays.copyOf(OBJDUMP_CMD, OBJDUMP_CMD.length + 1);
            cmd[cmd.length - 1] = binFile;
            objdump = PlatformUtils.runShell(cmd, outputObjdumpFile);

            System.out.println("Running readelf");
            File outputReadelfFile =
                new File(outputDir + File.separator + "readelf.log");
            cmd = Arrays.copyOf(READELF_CMD, READELF_CMD.length + 1);
            cmd[cmd.length - 1] = binFile;
            readelf = PlatformUtils.runShell(cmd, outputReadelfFile);
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
        catch (InterruptedException ie)
        {
            ie.printStackTrace();
            System.out.println(ie);
            System.exit(1);
        }

        System.out.println("Generating CFG");
        ISAModule module =
            new ISAModule(outputDir, entryFunctionName, cfgConfig);
        if (module.parseFunctions(readelf, objdump) != 0)
        {
            System.out.println("Failed to parse functions");
            System.exit(1);
        }

        if (module.hasRecursiveFunctionCalls())
        {
            module.writeCFGInDotRepresentation(null);
            module.writeFCGInDotRepresentation();
            System.out.println("The program is recursive!");
            System.exit(1);
        }

        System.out.println("Analyzing CFG");
        module.analyzeCFG();

        if (module.hasMissingInformation())
        {
            String outputConfig = outputDir + File.separator + "config.bwca";
            System.out.println("The program is missing annotations. Please "
                               + "fill in the missing information at "
                               + outputConfig + ", then run the program again "
                               + "with the -c argument.");
            module.writeCFGInDotRepresentation(null);
            module.writeFCGInDotRepresentation();
            module.writeMissingInfoConfig(outputConfig);
            System.exit(1);
        }

        for (Model model : models)
        {
            System.out.printf("Applying model '%s' from function '%s'\n",
                               model.getName(),
                               entryFunctionName);
            String solution = module.applyModel(model);

            System.out.println("    - Writing CFG .dot file");
            module.writeCFGInDotRepresentation(model);

            System.out.println("    - Writing FCG .dot file");
            module.writeFCGInDotRepresentation();

            System.out.printf("    - Solution: %s\n", solution);
        }
    }
}
