package com.bwca.cfg;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

import com.bwca.models.Model;
import com.bwca.utils.PlatformUtils;

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
    private String entryFunction;
    private CFGConfiguration config;
    private List<String> infoMsgs;

    static final String DOT_TOP_LEVEL = "digraph G {\n"
        + "    subgraph cluster_fcg {\n"
        + "        color = white;\n"
        + "        node [shape=box,style=filled,fillcolor=yellow];\n"
        + "        label = \"Function Call Graph\";\n"
        + "        labelloc = \"t\";\n"
        + "        legend [fillcolor=lightgrey,label=<<table "
        + "                                            border=\"0\""
        + "                                            cellpadding=\"4\""
        + "                                            cellspacing=\"6\""
        + "                                            cellborder=\"0\">\n"
        + "                <tr>\n"
        + "                    <td colspan=\"2\""
        + "                        align=\"center\">Legend</td>\n"
        + "                </tr>\n"
        + "                <tr>\n"
        + "                    <td align=\"left\">Entry Function</td>\n"
        + "                    <td bgcolor=\"red\">    </td>\n"
        + "                </tr>\n"
        + "                <tr>\n"
        + "                    <td align=\"left\">Regular Function</td>\n"
        + "                    <td bgcolor=\"yellow\">    </td>\n"
        + "                </tr>\n"
        + "            </table>>];\n"
        + "%s"
        + "%s"
        + "    }\n"
        + "}";

    public ISAModule(String outputDir,
                     String entryFunction,
                     CFGConfiguration config)
    {
        this.funcMap = new HashMap<String, ISAFunction>();
        this.outputDir = outputDir;
        this.config = config;
        this.entryFunction = entryFunction;
        this.infoMsgs = new LinkedList<String>();
    }

    public void writeMissingInfoConfig(String outputConfig)
    {
        try
        {
            // Write the data in ILP format
            FileWriter fwriter = new FileWriter(outputConfig);
            BufferedWriter bwriter = new BufferedWriter(fwriter);

            // Top level information (e.g. function sizes)
            bwriter.write("# Top level\n");
            bwriter.write(String.join("\n", infoMsgs) + "\n");
            if (infoMsgs.size() > 0)
            {
                bwriter.write("\n");
            }

            // Function information
            for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
            {
                List<String> msgs = entry.getValue().getMissingInfoMessages();
                if (msgs.size() < 1)
                {
                    continue;
                }

                bwriter.write("# Function " + entry.getKey() + "\n");
                bwriter.write(String.join("\n", msgs) + "\n\n");
            }

            bwriter.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
    }

    private Map<String, SymbolTableRecord> parseSymbolTable(
        List<String> readelf)
    {
        Map<String, SymbolTableRecord> symbolTable =
            new HashMap<String, SymbolTableRecord>();
        String name;
        long size;
        long addr;

        // Add the functions in the config file
        for (Map.Entry<String, Long> entry : config.getFunctions().entrySet())
        {
            name = entry.getKey();
            size = entry.getValue();

            // Do not try to work out the function address from the symbol
            // table because the readelf output has long function names clipped

            symbolTable.put(name, new SymbolTableRecord(size));
        }

        // Add functions from the readelf dump
        for (String record : readelf)
        {
            Matcher match = SYM_TABLE_FUNC.matcher(record);
            if (!match.matches())
            {
                // This is not an entry corresponding to a function symbol
                continue;
            }

            name = match.group("name");
            addr = Long.parseLong(match.group("address"), 16);
            size = Long.parseLong(match.group("size"), 10);
            if (size == 0)
            {
                continue;
            }

            symbolTable.put(name, new SymbolTableRecord(addr, size));
        }

        return symbolTable;
    }

    private int parseFunction(String name,
                              String parent,
                              Map<String, SymbolTableRecord> symbolTable,
                              ArrayList<String> objdump)
    {
        ISAFunction func;
        SymbolTableRecord symbol = symbolTable.get(name);
        Long size, addr;
        int ret = 0;

        // Parse the function with the given name
        if (symbol == null)
        {
            // Flag that we do not know anything about this function
            infoMsgs.add(String.format("function %s <size_bytes>", name));
            size = 0L;
            addr = 0L;
        }
        else
        {
            size = symbol.getSize();
            addr = symbol.getAddress();
        }

        if (addr == null)
        {
            func = new ISAFunction(size, name, config);
        }
        else
        {
            func = new ISAFunction(addr, size, name, config);
        }

        if (func.parseInstructions(objdump) != 0)
        {
            System.out.println("Something failed here");
            ret = -1;
        }
        if (funcMap.put(name, func) != null)
        {
            System.out.printf("Function %s found more than once in symbol "
                                  + "table\n",
                              name);
            System.exit(1);
        }

        // Construct dependency list on other functions
        func.buildFunctionCallDependencyList();

        // Parse the given function's dependencies
        for (String dependencyName : func.getFunctionCallDependencyNames())
        {
            if (funcMap.get(dependencyName) == null)
            {
                ret = (parseFunction(
                           dependencyName, name, symbolTable, objdump) != 0) ?
                    -1 :
                    ret;
            }
        }

        return ret;
    }

    public int parseFunctions(List<String> readelf, ArrayList<String> objdump)
    {
        // Parse the symbol table into a data structure that we can easily
        // look up function names on
        Map<String, SymbolTableRecord> symbolTable = parseSymbolTable(readelf);

        // Start parsing functions from the entry point onwards
        return parseFunction(entryFunction, null, symbolTable, objdump);
    }

    private boolean checkRecursion(Set<String> callStack, String stackTop)
    {
        ISAFunction topFunc = funcMap.get(stackTop);

        callStack.add(stackTop);

        for (String callee : topFunc.getFunctionCallDependencyNames())
        {
            if (callStack.contains(callee))
            {
                return true;
            }

            if (checkRecursion(callStack, callee))
            {
                return true;
            }
        }

        callStack.remove(stackTop);

        return false;
    }

    public boolean hasRecursiveFunctionCalls()
    {
        return checkRecursion(new HashSet<String>(), entryFunction);
    }

    public void analyzeCFG()
    {
        FunctionCallDetails call;

        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            entry.getValue().analyzeCFG();
        }

        // Traverse all functions and see if we have the information needed to
        // formulate and solve and ILP
        call = new FunctionCallDetails(entryFunction, 0, null);
        checkFunctionMissingInformation(call);
    }

    private void checkFunctionMissingInformation(FunctionCallDetails call)
    {
        ISAFunction func = funcMap.get(call.getCalleeName());

        for (FunctionCallDetails dep : func.getFunctionCallDependencies())
        {
            checkFunctionMissingInformation(dep);
        }

        func.checkMissingInformation(call);
    }

    public boolean hasMissingInformation()
    {
        if (infoMsgs.size() > 0)
        {
            return true;
        }

        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            List<String> msgs = entry.getValue().getMissingInfoMessages();
            if (msgs.size() > 0)
            {
                return true;
            }
        }

        return false;
    }

    public ISAFunction getFunction(String key)
    {
        return funcMap.get(key);
    }

    private void applyModelToFunction(Model model, FunctionCallDetails call)
    {
        ISAFunction func = funcMap.get(call.getCalleeName());
        String funcDir = outputDir + File.separator + func.getName();

        // Recursive solve the dependencies
        for (FunctionCallDetails dep : func.getFunctionCallDependencies())
        {
            applyModelToFunction(model, dep);
        }

        // Create the output directory for the ILP files of this function
        PlatformUtils.createOutputDirectory(funcDir);

        // Solve for this function
        func.applyModel(funcDir, model, call);
    }

    public String applyModel(Model model)
    {
        FunctionCallDetails call;

        call = new FunctionCallDetails(entryFunction, 0, null);
        applyModelToFunction(model, call);

        return model.getFunctionCallCost(call);
    }

    public void writeCFGInDotRepresentation()
    {
        // Write the CFGs for each function
        for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
        {
            String name = entry.getKey();
            ISAFunction func = entry.getValue();
            String outDir = outputDir + File.separator + name;

            PlatformUtils.createOutputDirectory(outDir);

            func.writeDotFile(outDir + File.separator + "partial.dot",
                              null,
                              null);
        }
    }

    public void writeFCGInDotRepresentation()
    {
        // Write the function call graph
        try
        {
            PlatformUtils.createOutputDirectory(outputDir);

            String filename = outputDir + File.separator + "fcg.dot";
            FileWriter fwriter = new FileWriter(filename);
            BufferedWriter bwriter = new BufferedWriter(fwriter);

            List<String> fcgNodes = new LinkedList<String>();
            List<String> fcgEdges = new LinkedList<String>();

            for (Map.Entry<String, ISAFunction> entry : funcMap.entrySet())
            {
                String name = entry.getKey();
                ISAFunction func = entry.getValue();

                // Add the function to the list of nodes
                String attrs = "";
                if (entryFunction.equals(name))
                {
                    attrs = ",fillcolor=red";
                }
                String node =
                    String.format("%s [label=\"%s\"%s]", name, name, attrs);
                fcgNodes.add(node);

                for (String callee : func.getFunctionCallDependencyNames())
                {
                    String edge = String.format("%s -> %s", name, callee);
                    fcgEdges.add(edge);
                }
            }

            String nodes = String.join(";\n        ", fcgNodes);
            nodes = "        " + nodes + ";\n";
            String edges = String.join(";\n        ", fcgEdges);
            if (edges.length() > 0)
            {
                edges = "        " + edges + ";\n";
            }
            String dot = String.format(DOT_TOP_LEVEL, nodes, edges);
            bwriter.write(dot);
            bwriter.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
    }
}
