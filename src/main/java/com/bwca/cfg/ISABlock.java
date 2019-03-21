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

import java.util.ArrayList;
import java.util.List;
import java.util.LinkedList;

import com.bwca.models.Model;

public class ISABlock
{
    private long startAddress;
    private ArrayList<ISALine> insts;
    private ArrayList<BranchTarget> edges;
    private int id;
    private boolean marked;
    private boolean exit;

    // Fields needed for loop detection
    // loopHeader: The entry block for a loop
    private boolean loopHeader;
    private ISABlock innerLoopHeader;
    private int dfsPosition;
    private int loopDepth;

    private List<FunctionCallDetails> funcCalls;

    public ISABlock(long startAddress, int id)
    {
        this.startAddress = startAddress;
        this.insts = new ArrayList<ISALine>();
        this.edges = null;
        this.id = id;
        this.marked = false;
        this.exit = false;

        this.loopHeader = false;
        this.innerLoopHeader = null;
        this.dfsPosition = 0;
        this.loopDepth = 0;

        funcCalls = new LinkedList<FunctionCallDetails>();
    }

    public LinkedList<String> getMissingInfoMessages()
    {
        LinkedList<String> msgs = new LinkedList<String>();

        for (ISALine line : insts)
        {
            msgs.addAll(line.getMissingInfoMessages());
        }

        return msgs;
    }

    public ArrayList<ISALine> getInstructions()
    {
        return insts;
    }

    public void setExit(boolean exit)
    {
        this.exit = exit;
    }

    public boolean isExit()
    {
        return exit;
    }

    public boolean isLoopHeader()
    {
        return loopHeader;
    }

    public void setLoopHeader(boolean loopHeader)
    {
        this.loopHeader = loopHeader;
    }

    public ISABlock getInnerLoopHeader()
    {
        return innerLoopHeader;
    }

    public void setInnerLoopHeader(ISABlock block)
    {
        innerLoopHeader = block;
    }

    public int getDFSPosition()
    {
        return dfsPosition;
    }

    public void setDFSPosition(int position)
    {
        dfsPosition = position;
    }

    public int getLoopDepth()
    {
        return loopDepth;
    }

    public void setLoopDepth(int depth)
    {
        loopDepth = depth;
    }

    public void setMark(boolean val)
    {
        marked = val;
    }

    public ArrayList<BranchTarget> getEdges()
    {
        return edges;
    }

    public boolean isMarked()
    {
        return marked;
    }

    public void addLine(ISALine inst)
    {
        insts.add(inst);
    }

    public ISALine getFirstLine()
    {
        int size = insts.size();
        if (size < 1)
        {
            System.out.println("Block has no instructions!");
            System.exit(1);
        }

        return insts.get(0);
    }

    public ISALine getLastLine()
    {
        int size = insts.size();
        if (size < 1)
        {
            System.out.println("Block has no instructions!");
            System.exit(1);
        }

        return insts.get(size - 1);
    }

    public void setEdges(ArrayList<BranchTarget> edges)
    {
        this.edges = edges;
    }

    public void addEdge(ISABlock block)
    {
        edges.add(new BranchTarget(block));
    }

    public int getId()
    {
        return id;
    }

    public void applyModel(Model model)
    {
        for (ISALine inst : insts)
        {
            model.addLineCost(this, inst);
        }

        for (BranchTarget edge : edges)
        {
            model.addEdgeCost(this, edge);
        }
    }

    public void buildFunctionCallDependencyList()
    {
        FunctionCallDetails call;
        String calleeName;
        long calleeAddr;

        for (ISALine inst : insts)
        {
            calleeName = inst.getTargetFunction();

            if (calleeName == null)
            {
                // We do not have this information, but the missing data would
                // have already been recorded earlier. We are doing this just
                // so that the user can get a nice printout of what information
                // we are missing to actually complete the analsys
                continue;
            }
            else if (inst.getType() == InstructionType.BRANCH ||
                     inst.getType() == InstructionType.COND_BRANCH)
            {
                // There are clearly not function calls
                continue;
            }

            calleeAddr = inst.getTargetFunctionAddress();

            call = new FunctionCallDetails(calleeName, calleeAddr, inst);
            funcCalls.add(call);
        }
    }

    public List<FunctionCallDetails> getFunctionCallDependencies()
    {
        return funcCalls;
    }

    public void nodesToString(ArrayList<String> output,
                              boolean isEntry,
                              String cost)
    {
        StringBuilder builder = new StringBuilder();
        List<String> attrs = new LinkedList<String>();
        String attrsStr = "";
        String nodeColor = "";

        if (isEntry)
        {
            attrs.add("entry");
        }
        if (exit)
        {
            attrs.add("exit");
        }
        if (innerLoopHeader != null || loopHeader)
        {
            if (innerLoopHeader != null)
            {
                attrs.add("loopHeader:block" + innerLoopHeader.getId());
            }
            else
            {
                attrs.add("loopHeader:null");
            }
            attrs.add("loopDepth:" + loopDepth);
        }
        if (cost != null)
        {
            attrs.add("cost:" + cost);
        }

        if (attrs.size() > 0)
        {
            attrsStr = "{" + String.join(",", attrs) + "}";
        }

        if (loopHeader)
        {
            nodeColor = "fillcolor=red,";
        }
        else if (innerLoopHeader != null)
        {
            nodeColor = "fillcolor=orange,";
        }

        builder.append(String.format(
            "block%d [%slabel=\"block%d%s\\l", id, nodeColor, id, attrsStr));
        for (ISALine inst : insts)
        {
            builder.append(inst.toString() + "\\l");
        }
        builder.append("\"]");

        output.add(builder.toString());
    }

    public String toString()
    {
        StringBuilder builder = new StringBuilder();

        builder.append("block" + id);
        for (ISALine inst : insts)
        {
            builder.append("\n" + inst.toString());
        }

        return builder.toString();
    }

    public void edgesToString(ArrayList<String> output, Model model)
    {
        for (BranchTarget edge : edges)
        {
            if (edge.getBlock() != null)
            {
                String edgeCost = null;
                String edgeColor = "black";
                if (model != null)
                {
                    edgeCost = model.getEdgeSummary(edge);
                }
                edgeCost = (edgeCost == null) ? "" : "{cost:" + edgeCost + "}";
                String edgeStr = String.format("block%d -> block%d "
                                                   + "[label=\"e%d %s\","
                                                   + "color=%s]",
                                               this.id,
                                               edge.getBlock().getId(),
                                               edge.getId(),
                                               edgeCost,
                                               edgeColor);
                output.add(edgeStr);
            }
        }
    }
}
