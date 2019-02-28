package com.bwca.cfg;

import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class CFGConfiguration
{
    static final Pattern CMD_EXIT =
        Pattern.compile("^exit\\s+"
                        + "(?<address>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})"
                        + "$");
    static final Pattern CMD_BRANCH =
        Pattern.compile("^branch\\s+"
                        + "(?<src>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})\\s+"
                        + "(?<dest>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})$");
    static final Pattern CMD_COMMENT = Pattern.compile("^#.*$");
    static final Pattern CMD_FUNC = Pattern.compile("^function\\s+"
                                                    + "(?<name>[a-zA-Z_]\\w*)"
                                                    + "\\s+(?<size>\\d+)$");
    static final Pattern CMD_LOOP_BOUND =
        Pattern.compile("^loopbound\\s+"
                        + "(?<address>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})"
                        + "\\s+min"
                        + "\\s+(?<min>\\d+)"
                        + "\\s+max"
                        + "\\s+(?<max>\\d+)$");
    static final Pattern CMD_ALLOC_BOUND =
        Pattern.compile("^allocbound\\s+"
                        + "(?<address>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})"
                        + "\\s+(?<size>\\d+)$");

    private Map<Long, BranchTarget> branchTargets;
    private Set<Long> exits;
    private Map<String, Long> funcs;
    private Map<Long, LoopBound> loops;
    private Map<Long, Long> allocs;

    public CFGConfiguration()
    {
        branchTargets = new HashMap<Long, BranchTarget>();
        exits = new HashSet<Long>();
        funcs = new HashMap<String, Long>();
        loops = new HashMap<Long, LoopBound>();
        allocs = new HashMap<Long, Long>();
    }

    public boolean isExit(long address)
    {
        return exits.contains(address);
    }

    public Map<String, Long> getFunctions()
    {
        return funcs;
    }

    public Map<Long, LoopBound> getLoopBounds()
    {
        return loops;
    }

    public Map<Long, Long> getAllocationSizes()
    {
        return allocs;
    }

    private long strToLong(String str)
    {
        int base = 10;
        if (str.length() > 2 && str.matches("^0[xX].+$"))
        {
            base = 16;
            str = str.substring(2);
        }
        return Long.parseLong(str, base);
    }

    public void loadFile(String filename)
    {
        BufferedReader reader;
        String line;
        Matcher match;

        try
        {
            reader = new BufferedReader(new FileReader(filename));
            do
            {
                line = reader.readLine();
                if (line == null)
                {
                    break;
                }
                else if (line.length() == 0)
                {
                    /* Skip over empty lines */
                    continue;
                }

                match = CMD_COMMENT.matcher(line);
                if (match.matches())
                {
                    /* Skip over comments */
                    continue;
                }

                match = CMD_LOOP_BOUND.matcher(line);
                if (match.matches())
                {
                    long address = strToLong(match.group("address"));
                    long lbound = strToLong(match.group("min"));
                    long ubound = strToLong(match.group("max"));
                    loops.put(address, new LoopBound(lbound, ubound));
                    continue;
                }

                match = CMD_ALLOC_BOUND.matcher(line);
                if (match.matches())
                {
                    long address = strToLong(match.group("address"));
                    long size = strToLong(match.group("size"));
                    allocs.put(address, size);
                    continue;
                }

                match = CMD_FUNC.matcher(line);
                if (match.matches())
                {
                    String name = match.group("name");
                    long size = strToLong(match.group("size"));
                    if (funcs.put(name, size) != null)
                    {
                        System.out.printf("Function %s more than once in "
                                              + "config\n",
                                          name);
                        System.exit(1);
                    }
                    continue;
                }

                match = CMD_EXIT.matcher(line);
                if (match.matches())
                {
                    exits.add(strToLong(match.group("address")));
                    continue;
                }

                match = CMD_BRANCH.matcher(line);
                if (match.matches())
                {
                    long src = strToLong(match.group("src"));
                    long dest = strToLong(match.group("dest"));
                    branchTargets.put(src, new BranchTarget(dest, null));
                    continue;
                }

                System.out.printf("Invalid command '%s'\n", line);
                System.exit(1);
            } while (true);

            reader.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
    }

    public void print()
    {
        System.out.println("Exits:");
        for (Long exit : exits)
        {
            System.out.printf("    0x%08x\n", exit);
        }

        System.out.println("Branch targets:");
        for (Map.Entry<Long, BranchTarget> entry : branchTargets.entrySet())
        {
            System.out.printf("    0x%08x -> (%s)\n",
                              entry.getKey(),
                              entry.getValue().toString());
        }

        System.out.println("Functions:");
        for (Map.Entry<String, Long> entry : funcs.entrySet())
        {
            System.out.printf(
                "    %s: %d\n", entry.getKey(), entry.getValue());
        }

        System.out.println("Allocation sizes:");
        for (Map.Entry<Long, Long> entry : allocs.entrySet())
        {
            System.out.printf(
                "    0x%08x: %d\n", entry.getKey(), entry.getValue());
        }
    }
}
