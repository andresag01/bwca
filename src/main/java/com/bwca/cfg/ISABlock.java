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

    public void nodesToString(ArrayList<String> output,
                              boolean isEntry,
                              String cost)
    {
        StringBuilder builder = new StringBuilder();
        StringBuilder attrs = new StringBuilder();
        String attrsStr = "";
        String nodeColor = "";
        String innerLoopHeaderStr = "";

        if (isEntry)
        {
            attrs.append("entry");
        }
        if (exit)
        {
            attrs.append((attrs.length() > 0) ? "," : "");
            attrs.append("exit");
        }
        if (innerLoopHeader != null)
        {
            attrs.append((attrs.length() > 0) ? "," : "");
            attrs.append("block" + innerLoopHeader.getId());
        }
        if (attrs.length() > 0)
        {
            attrsStr = " {" + attrs + "}";
        }

        if (loopHeader)
        {
            nodeColor = "fillcolor=red,";
        }
        else if (innerLoopHeader != null)
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
