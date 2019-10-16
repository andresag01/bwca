/**
 * MIT License
 *
 * Copyright (c) 2019 Andres Amaya Garcia, Kyriakos Georgiou
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
package com.bwca.cfg;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class CFGSolution
{
    private static final Pattern LP_SOLVE_SOLUTION =
        Pattern.compile("^Value of objective function:\\s+"
                        + "(?<solution>[0-9]+(\\.[0-9]+)?(e\\+\\d+)?)$");
    private static final Pattern LP_SOLVE_EDGE_SOLUTION =
        Pattern.compile("^e(?<id>\\d+)\\s+(?<solution>\\d+)$");
    private static final Pattern LP_SOLVE_BLOCK_SOLUTION =
        Pattern.compile("^b(?<id>\\d+)\\s+(?<solution>\\d+)$");

    private Map<Integer, Integer> edges;
    private Map<Integer, Integer> blocks;
    private String solution;

    public CFGSolution(List<String> lpSolveOutput)
    {
        edges = new HashMap<Integer, Integer>();
        blocks = new HashMap<Integer, Integer>();
        solution = null;

        parseLPSolveOutput(lpSolveOutput);
    }

    private void parseLPSolveOutput(List<String> lpSolveOutput)
    {
        // Parse the lp_solve output to get the result
        int edgeId, blockId;
        int edgeSolution, blockSolution;
        Matcher match;

        for (String line : lpSolveOutput)
        {
            match = LP_SOLVE_SOLUTION.matcher(line);
            if (match.matches())
            {
                solution = match.group("solution");
                continue;
            }

            match = LP_SOLVE_EDGE_SOLUTION.matcher(line);
            if (match.matches())
            {
                edgeId = Integer.parseInt(match.group("id"));
                edgeSolution = Integer.parseInt(match.group("solution"));
                edges.put(edgeId, edgeSolution);
                continue;
            }

            match = LP_SOLVE_BLOCK_SOLUTION.matcher(line);
            if (match.matches())
            {
                blockId = Integer.parseInt(match.group("id"));
                blockSolution = Integer.parseInt(match.group("solution"));
                blocks.put(blockId, blockSolution);
                continue;
            }
        }

        if (solution == null)
        {
            System.out.println("Solution file does not contain solution\n");
            System.exit(1);
        }
    }

    public String getObjectiveFunctionSolution()
    {
        return solution;
    }

    public int getEdgeSolution(int id)
    {
        if (edges.get(id) == null)
        {
            System.out.println("Edge " + id + " does not have a solution!");
            System.exit(1);
        }

        return edges.get(id);
    }

    public int getBlockSolution(int id)
    {
        if (blocks.get(id) == null)
        {
            System.out.println("Block " + id + " does not have a solution!");
            System.exit(1);
        }

        return blocks.get(id);
    }
}
