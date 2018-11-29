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

    Map<Long, BranchTarget> branchTargets;
    Set<Long> exits;

    public CFGConfiguration()
    {
        branchTargets = new HashMap<Long, BranchTarget>();
        exits = new HashSet<Long>();
    }

    public boolean isExit(long address)
    {
        return exits.contains(address);
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

                match = CMD_EXIT.matcher(line);
                if (match.matches())
                {
                    String addrStr = match.group("address");
                    int base = 10;
                    if (addrStr.length() > 2 && addrStr.matches("^0[xX].+$"))
                    {
                        base = 16;
                        addrStr = addrStr.substring(2);
                    }
                    long addr = Long.parseLong(addrStr, base);
                    exits.add(addr);
                    continue;
                }

                match = CMD_BRANCH.matcher(line);
                if (match.matches())
                {
                    String srcStr = match.group("src");
                    int base = 10;
                    if (srcStr.length() > 2 && srcStr.matches("^0[xX].+$"))
                    {
                        base = 16;
                        srcStr = srcStr.substring(2);
                    }
                    long src = Long.parseLong(srcStr, base);

                    String destStr = match.group("dest");
                    base = 10;
                    if (destStr.length() > 2 && srcStr.matches("^0[xX].$"))
                    {
                        base = 16;
                        destStr = destStr.substring(2);
                    }
                    long dest = Long.parseLong(destStr, base);

                    branchTargets.put(src, new BranchTarget(dest, null));
                    continue;
                }

                System.out.printf("Invalid command '%s'\n", line);
                System.exit(1);
            }
            while (true);

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
    }
}
