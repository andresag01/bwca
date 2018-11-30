package com.bwca.cfg;

import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;

import com.bwca.models.Model;

public class ISAModule
{
    static final Pattern SYM_TABLE_FUNC =
        Pattern.compile("^\\s+\\d+:"
                        + "\\s+(?<address>[0-9a-fA-F]+)"
                        + "\\s+(?<size>\\d+)"
                        + "\\s+FUNC"
                        + "\\s+(LOCAL|GLOBAL|WEAK)"
                        + "\\s+(DEFAULT|PROTECTED|HIDDEN|INTERNAL)"
                        + "\\s+(ABS|\\d+)"
                        + "\\s+(?<name>.+)$");

    private Map<String, ISAFunction> funcMap;
    private String outputDir;
    private CFGConfiguration config;

    public ISAModule(String outputDir, CFGConfiguration config)
    {
        this.funcMap = new HashMap<String, ISAFunction>();
        this.outputDir = outputDir;
        this.config = config;
    }

    public void printMissingInfoMessages()
    {
        System.out.println("Parsed " + funcMap.size() + " functions");
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            System.out.printf("Function %s\n", entry.getKey());
            for (String msg : entry.getValue().getMissingInfoMessages())
            {
                System.out.printf("    %s\n", msg);
            }
        }
    }

    public int parseFunctions(ArrayList<String> readelf,
                              ArrayList<String> objdump)
    {
        int ret = 0;

        // Add the functions in the config file
        for (Map.Entry<String, Long> entry : config.getFunctions().entrySet())
        {
            String name = entry.getKey();
            long size = entry.getValue();

            ISAFunction func = new ISAFunction(size, name, config);
            if (func.parseInstructions(objdump) != 0)
            {
                ret = -1;
            }
            if (funcMap.put(name, func) != null)
            {
                System.out.printf("Function %s in config found twice\n", name);
                System.exit(1);
            }
        }

        // Add the functions in the symbol table (readelf output)
        for (String line : readelf)
        {
            Matcher match = SYM_TABLE_FUNC.matcher(line);
            if (!match.matches())
            {
                // This is not an entry corresponding to a function symbol
                continue;
            }

            String name = match.group("name");
            long size = Long.parseLong(match.group("size"), 10);
            if (size == 0)
            {
                continue;
            }
            ISAFunction func = new ISAFunction(
                                    Long.parseLong(match.group("address"), 16),
                                    size,
                                    name,
                                    config);
            if (func.parseInstructions(objdump) != 0)
            {
                ret = -1;
            }
            if (funcMap.put(name, func) != null)
            {
                System.out.printf("Function %s in config found twice\n", name);
                System.exit(1);
            }
        }

        return ret;
    }

    public void analyzeCFG()
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            entry.getValue().analyzeCFG();
        }
    }

    public ISAFunction getFunction(String key)
    {
        return funcMap.get(key);
    }

    public void applyModel(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            ISAFunction func = entry.getValue();
            func.applyModel(model);
        }
    }

    private void createOutputDirectory(String directory)
    {
        File dir = new File(directory);

        if (dir.isDirectory())
        {
            // The directory already exists, nothing to do
            return;
        }

        // Create the directory (and any parent directories that do not exist)
        if (!dir.mkdirs())
        {
            System.out.println("Could not create output directory " +
                               outputDir);
            System.exit(1);
        }
    }

    public void writeILP(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            String name = entry.getKey();
            ISAFunction func = entry.getValue();
            String outDir = outputDir + File.separator + name;

            createOutputDirectory(outDir);

            func.writeILP(
                outDir + File.separator + model.getName() + ".lp", model);
        }
    }

    public void writeDotRepresentation(Model model)
    {
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            String name = entry.getKey();
            ISAFunction func = entry.getValue();
            String outDir = outputDir + File.separator + name;

            createOutputDirectory(outDir);

            if (model != null)
            {
                func.writeDotFile(
                    outDir + File.separator + model.getName() + ".dot", model);
            }
            else
            {
                func.writeDotFile( outDir + ".dot", null);
            }
        }
    }
}
