package com.bwca.cfg;

import java.util.ArrayList;

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
    // loopBranch: The block with the conditional branch to iterate
    private boolean loopBranch;
    // intermediateLoopBranch: Signals that this block is a node that is
    // between the loop "return" conditional branch and the loop header
    private boolean intermediateLoopBranch;
    private ISABlock innerLoopHeader;
    private int dfsPosition;

    public ISABlock(long startAddress, int id)
    {
        this.startAddress = startAddress;
        this.insts = new ArrayList<ISALine>();
        this.edges = null;
        this.id = id;
        this.marked = false;
        this.exit = false;

        this.loopHeader = false;
        this.intermediateLoopBranch = false;
        this.loopBranch = false;
        this.innerLoopHeader = null;
        this.dfsPosition = 0;
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

    public boolean isLoopBranch()
    {
        return loopBranch;
    }

    public void setLoopBranch(boolean loopBranch)
    {
        this.loopBranch = loopBranch;
    }

    public boolean isLoopHeader()
    {
        return loopHeader;
    }

    public void setLoopHeader(boolean loopHeader)
    {
        this.loopHeader = loopHeader;
    }

    public boolean isIntermediateLoopBranch()
    {
        return intermediateLoopBranch;
    }

    public void setIntermediateLoopBranch(boolean intermediateLoopBranch)
    {
        this.intermediateLoopBranch = intermediateLoopBranch;
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

    public void nodesToString(ArrayList<String> output,
                              boolean isEntry,
                              String cost)
    {
        StringBuilder builder = new StringBuilder();
        StringBuilder attrs = new StringBuilder();
        String attrsStr = "";
        String nodeColor = "";

        if (isEntry)
        {
            attrs.append("entry");
        }
        else if (exit)
        {
            attrs.append((attrs.length() > 0) ? "," : "");
            attrs.append("exit");
        }
        if (attrs.length() > 0)
        {
            attrsStr = " {" + attrs + "}";
        }

        if (loopBranch)
        {
            nodeColor = "fillcolor=red,";
        }
        else if (loopHeader || intermediateLoopBranch)
        {
            nodeColor = "fillcolor=orange,";
        }

        if (cost != null)
        {
            cost = " [" + cost + "]";
        }
        else
        {
            cost = "";
        }

        builder.append(String.format("block%d [%slabel=\"block%d%s%s\\l",
                                     id,
                                     nodeColor,
                                     id,
                                     attrsStr,
                                     cost));
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

    public void edgesToString(ArrayList<String> output,
                              Model model)
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
                edgeCost = (edgeCost == null) ? "" : "[" + edgeCost + "]";
                if (edge.getLoopExit())
                {
                    edgeColor = "blue";
                }
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
