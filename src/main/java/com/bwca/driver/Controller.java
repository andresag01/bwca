package com.bwca.driver;

import java.io.InputStream;
import java.io.InputStreamReader;
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
import com.bwca.models.WCAModel;
import com.bwca.models.WCMAModel;
import com.bwca.cfg.ISAModule;

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
    private Set<String> selectedModels;
    private List<Model> models;

    public static final String[][] MODELS = {
        { "wcet", "Worst-Case Execution Time" },
        { "wca", "Worst-Case Allocation" },
        { "wcma", "Worst-Case Memory Access" },
    };
    private static final String HELP_MSG = "Bristol Worst Case Analysis Tool\n"
        + "\n"
        + "ARGUMENTS:\n"
        + "    -b    Binary file to analyze.\n"
        + "    -o    Directory to store output files.\n"
        + "    -h    Prints this help message\n"
        + "    -l    Print a list of options for -m\n"
        + "    -m    Analyze the binary file with the specified model.\n"
        + "          Repeat this option as many times as needed to apply \n"
        + "          more than one model. Run the program with -l to view a\n"
        + "          list of options.";

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
        selectedModels = new HashSet<String>();
        models = new LinkedList<Model>();
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
        for (String modelOption : selectedModels)
        {
            switch (modelOption)
            {
                case "wcet":
                    models.add(new WCETModel());
                    break;

                case "wca":
                    models.add(new WCAModel());
                    break;

                case "wcma":
                    models.add(new WCMAModel());
                    break;

                default:
                    System.out.println("Unrecognized model " + modelOption);
                    fail = true;
                    break;
            }
        }

        if (fail)
        {
            System.exit(1);
        }
    }

    private ArrayList<String> runShell(String[] cmd)
        throws InterruptedException, IOException
    {
        Runtime rt = Runtime.getRuntime();
        Process proc = rt.exec(cmd);

        InputStream stdoutStream = proc.getInputStream();
        InputStreamReader stdoutReader = new InputStreamReader(stdoutStream);
        BufferedReader stdout = new BufferedReader(stdoutReader);

        InputStream stderrStream = proc.getErrorStream();
        InputStreamReader stderrReader = new InputStreamReader(stderrStream);
        BufferedReader stderr = new BufferedReader(stderrReader);

        // Wait for the subprocess to terminate
        int retStatus = proc.waitFor();

        // Read the errors
        String line = null;
        boolean failed = false;
        while ((line = stderr.readLine()) != null)
        {
            System.out.println(line);
            failed = true;
        }

        if (failed)
        {
            System.out.println("Subprocess terminated with errors");
            System.exit(1);
        }

        if (retStatus != 0)
        {
            System.out.println("Subprocess terminated with error " +
                               retStatus);
            System.exit(1);
        }

        // Read the output
        ArrayList<String> output = new ArrayList<String>();
        while ((line = stdout.readLine()) != null)
        {
            output.add(line);
        }

        return output;
    }

    private void analyze()
    {
        ArrayList<String> objdump = null;
        ArrayList<String> readelf = null;

        try
        {
            System.out.println("Running objdump");
            String[] cmd = Arrays.copyOf(OBJDUMP_CMD, OBJDUMP_CMD.length + 1);
            cmd[cmd.length - 1] = binFile;
            objdump = runShell(cmd);

            System.out.println("Running readelf");
            cmd = Arrays.copyOf(READELF_CMD, READELF_CMD.length + 1);
            cmd[cmd.length - 1] = binFile;
            readelf = runShell(cmd);
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
        ISAModule module = new ISAModule(outputDir);
        module.parseFunctions(readelf, objdump);

        System.out.println("Analyzing CFG");
        module.analyzeCFG();

        for (Model model : models)
        {
            System.out.println("Applying model " + model.getName());
            module.applyModel(model);

            System.out.println("    - Writing .dot file");
            module.writeDotRepresentation(model);
            System.out.println("    - Writing .lp file");
            module.writeILP(model);
        }
    }
}
