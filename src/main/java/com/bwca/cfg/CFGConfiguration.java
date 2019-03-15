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
    static final Pattern CMD_BRANCH =
        Pattern.compile("^branch\\s+"
                        + "(?<src>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})\\s+"
                        + "(?<dest>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})$");
    static final Pattern CMD_UNFEASIBLE_BRANCH =
        Pattern.compile("^unfeasible\\s+branch\\s+"
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
                        + "\\s+(?<max>\\d+)"
                        + "\\s+from\\s+call\\s+"
                        + "(?<call>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})$");
    static final Pattern CMD_FUNC_CALL =
        Pattern.compile("^call\\s+"
                        + "(?<src>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})\\s+"
                        + "(?<dest>[a-zA-Z_]\\w*)$");
    static final Pattern CMD_ALLOC_SIZE =
        Pattern.compile("^allocation\\s+"
                        + "(?<address>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})"
                        + "\\s+(?<size>\\d+)"
                        + "\\s+from\\s+call\\s+"
                        + "(?<call>0(x|X)[0-9A-Fa-f]{1,8}|(0d)?[0-9]{1,})$");

    private Map<Long, BranchTarget> branchTargets;
    private Map<Long, BranchTarget> unfeasibleBranchTargets;
    private Map<String, Long> funcs;
    private Map<Long, Map<Long, LoopBound>> loops;
    private Map<Long, String> functionCalls;
    private Map<Long, Map<Long, Long>> allocs;

    public CFGConfiguration()
    {
        branchTargets = new HashMap<Long, BranchTarget>();
        unfeasibleBranchTargets = new HashMap<Long, BranchTarget>();
        funcs = new HashMap<String, Long>();
        loops = new HashMap<Long, Map<Long, LoopBound>>();
        functionCalls = new HashMap<Long, String>();
        allocs = new HashMap<Long, Map<Long, Long>>();
    }

    public String getFunctionCalleeName(long address)
    {
        return functionCalls.get(address);
    }

    public Long getAllocationSize(long callAddress, long allocAddress)
    {
        Map<Long, Long> map = allocs.get(callAddress);
        return (map == null) ? null : map.get(allocAddress);
    }

    public Map<String, Long> getFunctions()
    {
        return funcs;
    }

    public LoopBound getLoopBounds(long callAddress, long loopAddress)
    {
        Map<Long, LoopBound> map = loops.get(callAddress);
        return (map == null) ? null : map.get(loopAddress);
    }

    public Long getBranchDestination(long address)
    {
        BranchTarget branch = branchTargets.get(address);
        return (branch == null) ? null : branch.getAddress();
    }

    public Long getUnfeasibleBranchDestination(long address)
    {
        BranchTarget branch = unfeasibleBranchTargets.get(address);
        return (branch == null) ? null : branch.getAddress();
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
                    long callAddress = strToLong(match.group("call"));
                    Map<Long, LoopBound> map;
                    if (!loops.containsKey(callAddress))
                    {
                        map = new HashMap<Long, LoopBound>();
                        loops.put(callAddress, map);
                    }
                    else
                    {
                        map = loops.get(callAddress);
                    }
                    map.put(address, new LoopBound(lbound, ubound));
                    continue;
                }

                match = CMD_ALLOC_SIZE.matcher(line);
                if (match.matches())
                {
                    long address = strToLong(match.group("address"));
                    long size = strToLong(match.group("size"));
                    long callAddress = strToLong(match.group("call"));
                    Map<Long, Long> map;
                    if (!allocs.containsKey(callAddress))
                    {
                        map = new HashMap<Long, Long>();
                        allocs.put(callAddress, map);
                    }
                    else
                    {
                        map = allocs.get(callAddress);
                    }
                    map.put(address, size);
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

                match = CMD_UNFEASIBLE_BRANCH.matcher(line);
                if (match.matches())
                {
                    long src = strToLong(match.group("src"));
                    long dest = strToLong(match.group("dest"));
                    if ((src & 0x1) != 0)
                    {
                        System.out.printf("Source address at '%s' is not "
                                              +
                                              "aligned to halfword boundary\n",
                                          src);
                        System.exit(1);
                    }
                    if ((dest & 0x1) != 0)
                    {
                        System.out.printf("Destination address at '%s' is not "
                                              +
                                              "aligned to halfword boundary\n",
                                          dest);
                        System.exit(1);
                    }
                    unfeasibleBranchTargets.put(src,
                                                new BranchTarget(dest, null));
                    continue;
                }

                match = CMD_BRANCH.matcher(line);
                if (match.matches())
                {
                    long src = strToLong(match.group("src"));
                    long dest = strToLong(match.group("dest"));
                    if ((src & 0x1) != 0)
                    {
                        System.out.printf("Source address at '%s' is not "
                                              +
                                              "aligned to halfword boundary\n",
                                          src);
                        System.exit(1);
                    }
                    if ((dest & 0x1) != 0)
                    {
                        System.out.printf("Destination address at '%s' is not "
                                              +
                                              "aligned to halfword boundary\n",
                                          dest);
                        System.exit(1);
                    }
                    branchTargets.put(src, new BranchTarget(dest, null));
                    continue;
                }

                match = CMD_FUNC_CALL.matcher(line);
                if (match.matches())
                {
                    long src = strToLong(match.group("src"));
                    String callee = match.group("name");
                    functionCalls.put(src, callee);
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
        System.out.println("Loop bounds:");
        for (Map.Entry<Long, Map<Long, LoopBound>> entry : loops.entrySet())
        {
            System.out.printf("    0x%08x:\n", entry.getKey());

            Map<Long, LoopBound> boundMap = entry.getValue();
            for (Map.Entry<Long, LoopBound> lentry : boundMap.entrySet())
            {
                System.out.printf("        0x%08x [%d, %d]\n",
                                  lentry.getKey(),
                                  lentry.getValue().getLowerBound(),
                                  lentry.getValue().getUpperBound());
            }
        }

        System.out.println("Allocation size bounds:");
        for (Map.Entry<Long, Map<Long, Long>> entry : allocs.entrySet())
        {
            System.out.printf("    0x%08x:\n", entry.getKey());

            Map<Long, Long> boundMap = entry.getValue();
            for (Map.Entry<Long, Long> lentry : boundMap.entrySet())
            {
                System.out.printf("        0x%08x [%d]\n",
                                  lentry.getKey(),
                                  lentry.getValue());
            }
        }

        System.out.println("Branch targets:");
        for (Map.Entry<Long, BranchTarget> entry : branchTargets.entrySet())
        {
            System.out.printf("    0x%08x -> (%s)\n",
                              entry.getKey(),
                              entry.getValue().toString());
        }

        System.out.println("Unfeasible branch targets:");
        for (Map.Entry<Long, BranchTarget> entry :
             unfeasibleBranchTargets.entrySet())
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
    }
}
