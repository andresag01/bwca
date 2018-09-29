package com.bwca.cfg;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.List;
import java.util.LinkedList;
import java.util.ListIterator;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

import com.bwca.models.Model;

public class ISAFunction
{
    static final Pattern INST =
        Pattern.compile("^\\s+(?<address>[0-9a-fA-F]+):"
                        + "\\s+[0-9a-fA-F]{4}(\\s+[0-9a-fA-F]{4})?"
                        + "\\s+(?<opcode>\\w+)(\\.n)?(?<body>.*)$");
    static final Pattern FUNC = Pattern.compile("^(?<address>[0-9a-fA-F]+)\\s+"
                                                + "<(?<name>[^>]+)>:$");
    static final String DOT_TOP_LEVEL = "digraph G {\n"
        + "    subgraph cluster {\n"
        + "        style=filled;\n"
        + "        node [shape=box,style=filled,fillcolor=yellow];\n"
        + "        label = \"%s\";\n"
        + "%s"
        + "%s"
        + "    }\n"
        + "}";
    static final String ILP_TOP_LEVEL = "/* ILP for function %s */\n\n"
        + "/* Problem */\n"
        + "max: %s;\n\n"
        + "/* Output constraints */\n"
        + "%s\n"
        + "/* Input constraints */\n"
        + "%s\n"
        + "/* Loop constraints */\n"
        + "%s";

    private long address;
    private long size;
    private String name;
    private ISABlock entry;
    private ArrayList<ISABlock> blocks;
    private int nextBlockId;

    public ISAFunction(long address, long size, String name)
    {
        // Clear the bottom bit of the address because this is thumb
        this.address = address & ~0x1;
        this.size = size;
        this.name = name;
        this.entry = null;
        this.nextBlockId = 0;
    }

    public void parseInstructions(ArrayList<String> objdump)
    {
        boolean foundFunc = false;
        int funcIndex;

        // Find the start of the function in the code
        for (funcIndex = 0; funcIndex < objdump.size(); funcIndex++)
        {
            long address;
            String name;

            Matcher funcMatch = FUNC.matcher(objdump.get(funcIndex));
            if (funcMatch.matches())
            {
                address = Long.parseLong(funcMatch.group("address"), 16);
                name = funcMatch.group("name");
            }
            else
            {
                // This is not the start of a function
                continue;
            }

            // Check that the function values match the symbol table data
            if (!this.name.equals(name))
            {
                // Not the correct function
                continue;
            }
            else if (address != this.address)
            {
                // Something went wrong
                String err =
                    String.format("Function at 0x%08X does not "
                                      + "match address at symbol table "
                                      + "0x%08X for function %s",
                                  address,
                                  this.address,
                                  name);
                System.out.println(err);
                System.exit(1);
            }

            // Found the start of the function
            foundFunc = true;
            break;
        }

        if (!foundFunc)
        {
            System.out.println("Could not find function " + this.name + " "
                               + "in input binary");
            System.exit(1);
        }

        // Parse the instructions
        ArrayList<ISALine> insts = new ArrayList<ISALine>();
        for (funcIndex = funcIndex + 1; funcIndex < objdump.size();
             funcIndex++)
        {
            Matcher instMatch = INST.matcher(objdump.get(funcIndex));
            if (!instMatch.matches())
            {
                // This is not an instruction, skip it
                continue;
            }

            long address = Long.parseLong(instMatch.group("address"), 16);
            String opcode = instMatch.group("opcode");
            String body = instMatch.group("body");

            // Check that we have have not gone past the end of the function
            if (address >= this.address + this.size)
            {
                break;
            }

            // Create the instruction
            insts.add(new ISALine(address, opcode, body));
        }

        // Extract target addresses
        Set<Long> branchTargetAddrs = new HashSet<Long>();
        for (ISALine inst : insts)
        {
            if (inst.getType() == InstructionType.BRANCH ||
                inst.getType() == InstructionType.COND_BRANCH)
            {
                for (BranchTarget target : inst.getBranchTargets())
                {
                    // Add it do the map if the target is in the function
                    Long destAddress = target.getAddress();
                    if (destAddress != null && destAddress >= this.address &&
                        destAddress < this.address + this.size)
                    {
                        branchTargetAddrs.add(destAddress);
                    }
                }
            }
        }

        // Create the blocks of the CFG
        blocks = new ArrayList<ISABlock>();
        HashMap<Long, ISABlock> blocksMap = new HashMap<Long, ISABlock>();
        ISABlock cur = null;
        for (ISALine inst : insts)
        {
            // Inspect the instruction and do the following:
            //   - If this address is a branch target create a new block
            //   - Otherwise add the current instruction to the last block

            long address = inst.getAddress();
            if (branchTargetAddrs.contains(address))
            {
                // This is the start of a block
                cur = new ISABlock(address, nextBlockId++);
                blocks.add(cur);
                blocksMap.put(address, cur);
                cur.addLine(inst);
            }
            else
            {
                // Add the instruction to the end of the current block
                if (cur == null)
                {
                    // This can happen if the function's entry point is not the
                    // target of a branch within the function
                    cur = new ISABlock(address, nextBlockId++);
                    blocks.add(cur);
                    blocksMap.put(address, cur);
                }
                cur.addLine(inst);
            }

            // Terminate the current block if the current instruction is a
            // branch to an unknown location without a return or a branch
            // (regardless of condition) within the function
            if (inst.getType() == InstructionType.BRANCH &&
                inst.getType() == InstructionType.COND_BRANCH)
            {
                cur = null;
            }
        }

        if (blocks.size() < 1)
        {
            System.out.println("The function has no blocks!");
            System.exit(1);
        }
        entry = blocks.get(0);

        // Create the edges of the CFG
        for (int i = 0; i < blocks.size(); i++)
        {
            // Look up the last instruction of every block:
            //   - If it is a regular instruction then add an edge to next
            //   - If it is an unconditional branch then add an edge to the
            //     appropriate block
            //   - If it is a conditional branch then add an edge to the next
            //     block and the appropriate target block (which should be
            //     different!
            ISALine inst = blocks.get(i).getLastLine();
            switch (inst.getType())
            {
                case OTHER:
                case BRANCH_LINK:
                    blocks.get(i).setEdges(inst.getBranchTargets());
                    if (i + 1 >= blocks.size())
                    {
                        // We are at the end of the function. Set this as an
                        // exit, but it is weird to not have a function
                        // terminate with a return instruction.
                        //
                        // NOTE: Sometimes the last instruction can be a call
                        // to a noreturn function, in which case there could be
                        // a blx or bl instruction at the end.
                        blocks.get(i).setExit(true);
                        break;
                    }
                    // TODO: Initialize the branch target with more information
                    blocks.get(i).addEdge(blocks.get(i + 1));
                    break;

                case BRANCH:
                case COND_BRANCH:
                    blocks.get(i).setEdges(inst.getBranchTargets());
                    if (inst.getBranchTargets().size() == 0)
                    {
                        // This is an exit block
                        blocks.get(i).setExit(true);
                        break;
                    }
                    for (BranchTarget target : inst.getBranchTargets())
                    {
                        Long address = target.getAddress();
                        if (address != null)
                        {
                            target.setBlock(blocksMap.get(address));
                        }
                    }
                    break;

                default:
                    System.out.println("Invalid instruction type");
                    System.exit(1);
            }
        }
    }

    private void garbageCollectBlocks()
    {
        // Clear all mark flags
        for (ISABlock block : blocks)
        {
            block.setMark(false);
        }

        // - The root set is the entry block
        // - Traverse all reachable blocks starting from entry and mark them
        // - Collect all the unmarked (dead) objects
        entry.setMark(true);
        markBlocks(entry);
        sweepBlocks();
    }

    private void markBlocks(ISABlock block)
    {
        for (BranchTarget edge : block.getEdges())
        {
            ISABlock target = edge.getBlock();
            if (target != null && !target.isMarked())
            {
                target.setMark(true);
                markBlocks(target);
            }
        }
    }

    private void sweepBlocks()
    {
        ArrayList<ISABlock> clean = new ArrayList<ISABlock>();
        for (int i = 0; i < blocks.size(); i++)
        {
            if (blocks.get(i).isMarked())
            {
                blocks.get(i).setMark(false);
                clean.add(blocks.get(i));
            }
        }
        blocks = clean;
    }

    private void numberEdges()
    {
        int nextEdgeId = 0;

        for (ISABlock block : blocks)
        {
            if (block.getEdges().size() == 0)
            {
                continue;
            }

            for (BranchTarget edge : block.getEdges())
            {
                edge.setId(nextEdgeId++);
            }
        }
    }

    public void analyzeCFG()
    {
        detectLoops();
        garbageCollectBlocks();
        numberEdges();
    }

    private void detectLoops()
    {
        // The algorithm is taken from the paper at:
        // https://lenx.100871.net/papers/loop-SAS.pdf

        // Initialize everything to zero/null/false
        for (ISABlock block : blocks)
        {
            block.setLoopHeader(false);
            block.setInnerLoopHeader(null);
            block.setDFSPosition(0);
            block.setMark(false);
        }
        traverseInDFS(entry, 1);
    }

    private ISABlock traverseInDFS(ISABlock block, int dfsPosition)
    {
        block.setDFSPosition(dfsPosition);
        block.setMark(true);

        for (BranchTarget target : block.getEdges())
        {
            ISABlock successor = target.getBlock();

            if (!successor.isMarked())
            {
                // Case A
                ISABlock nh = traverseInDFS(successor, dfsPosition + 1);
                tagLoopHeader(block, nh);
            }
            else
            {
                if (successor.getDFSPosition() > 0)
                {
                    // Case B
                    successor.setLoopHeader(true);
                    block.setLoopBranch(true);
                    tagLoopHeader(block, successor);
                }
                else if (successor.getInnerLoopHeader() == null)
                {
                    // Case C: Do nothing...
                }
                else
                {
                    ISABlock header = successor.getInnerLoopHeader();
                    if (header.getDFSPosition() > 0)
                    {
                        // Case D
                        tagLoopHeader(block, header);
                    }
                    else
                    {
                        // Case E: Reentry...
                        // The loop is irreducible
                        System.out.println("There is an irreducible loop!");
                        System.exit(1);
                    }
                }
            }
        }
        block.setDFSPosition(0);
        return block.getInnerLoopHeader();
    }

    private void tagLoopHeader(ISABlock block, ISABlock header)
    {
        if (block == header || header == null)
        {
            return;
        }

        ISABlock cur1 = block;
        ISABlock cur2 = header;

        while (cur1.getInnerLoopHeader() != null)
        {
            ISABlock ih = cur1.getInnerLoopHeader();

            if (ih == cur2)
            {
                return;
            }

            if (ih.getDFSPosition() < cur2.getDFSPosition())
            {
                cur1.setInnerLoopHeader(cur2);
                cur1 = cur2;
                cur2 = ih;
            }
            else
            {
                cur1 = ih;
            }
        }
        cur1.setInnerLoopHeader(cur2);
    }

    public void applyModel(Model model)
    {
        for (ISABlock block : blocks)
        {
            block.applyModel(model);
        }
    }

    public void writeILP(String filename, Model model)
    {
        try
        {
            FileWriter fwriter = new FileWriter(filename);
            BufferedWriter bwriter = new BufferedWriter(fwriter);
            StringBuilder outConstraints = new StringBuilder();
            StringBuilder inConstraints = new StringBuilder();
            StringBuilder loopConstraints = new StringBuilder();

            // Make the string for the problem
            List<String> problem = new LinkedList<String>();
            for (ISABlock block : blocks)
            {
                StringBuilder blockCost = new StringBuilder();
                blockCost.append(model.getPositiveBlockCost(block));
                blockCost.append(" b" + block.getId());

                for (BranchTarget edge : block.getEdges())
                {
                    String neg = model.getNegativeEdgeCost(edge);
                    if (neg != null)
                    {
                        blockCost.append(" - " + neg + " e" + edge.getId());
                    }
                }

                problem.add(blockCost.toString());
            }
            String intercept = model.getInterceptCost();
            String problemStr = String.join("\n     + ", problem) +
                ((intercept != null) ? "\n     " + intercept : "");

            // Make the string for the constraints
            //  - Add an output constraint for every block
            //  - Construct a table of block -> inEdge
            //  - Add an input constraint for every block
            Map<ISABlock, List<String>> inEdges =
                new HashMap<ISABlock, List<String>>();

            // Output constraints
            for (ISABlock block : blocks)
            {
                outConstraints.append("b" + block.getId() + " = ");
                if (block.getEdges().size() == 0)
                {
                    if (!block.isExit())
                    {
                        System.out.println("Block " + block.getId() + " has "
                                           + "no output edges and not an "
                                           + "exit in function " + name +
                                           "\n");
                        System.exit(1);
                    }
                    outConstraints.append("1;\n");
                    continue;
                }

                List<String> outEdges = new LinkedList<String>();
                for (BranchTarget edge : block.getEdges())
                {
                    outEdges.add("e" + edge.getId());

                    ISABlock target = edge.getBlock();
                    List<String> inEdgeList = inEdges.get(target);
                    if (inEdgeList == null)
                    {
                        inEdgeList = new LinkedList<String>();
                        inEdges.put(target, inEdgeList);
                    }
                    inEdgeList.add("e" + edge.getId());
                }
                outConstraints.append(String.join(" + ", outEdges) + ";\n");
            }

            // Input constraints
            for (ISABlock block : blocks)
            {
                List<String> edges = inEdges.get(block);
                if (edges == null && block != entry)
                {
                    System.out.println("Block has no input edges and is not "
                                       + "entry point");
                    System.exit(1);
                }
                else if (edges == null)
                {
                    // This is the entry block, so there is a chance that it
                    // does not have any in edges. To avoid a null pointer
                    // exception we create a list so that the join() operation
                    // later on works without problems
                    edges = new LinkedList<String>();
                }

                inConstraints.append("b" + block.getId() + " = ");

                if (block == entry)
                {
                    edges.add("1");
                }
                inConstraints.append(String.join(" + ", edges) + ";\n");
            }

            // Loop constraints
            for (ISABlock block : blocks)
            {
                if (block.isLoopBranch())
                {
                    for (BranchTarget edge : block.getEdges())
                    {
                        if (!edge.getCondition())
                        {
                            // This is the exit edge of the loop
                            String constraint =
                                String.format("b%d >= BOUND e%d;\n",
                                              block.getId(),
                                              edge.getId());
                            loopConstraints.append(constraint);

                            constraint = String.format("b%d <= BOUND e%d;\n",
                                                       block.getId(),
                                                       edge.getId());
                            loopConstraints.append(constraint);
                        }
                    }
                }
            }

            // Write the data in ILP format
            String output = String.format(ILP_TOP_LEVEL,
                                          name,
                                          problemStr,
                                          outConstraints.toString(),
                                          inConstraints.toString(),
                                          loopConstraints.toString());
            bwriter.write(output);
            bwriter.close();
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
    }

    public void writeDotFile(String filename, Model model)
    {
        try
        {
            FileWriter fwriter = new FileWriter(filename);
            BufferedWriter bwriter = new BufferedWriter(fwriter);

            // Compose the string to write
            ArrayList<String> nodes = new ArrayList<String>();
            ArrayList<String> edges = new ArrayList<String>();
            for (ISABlock block : blocks)
            {
                String cost = null;
                if (model != null)
                {
                    cost = model.getBlockSummary(block);
                }
                block.nodesToString(nodes, block == entry, cost);
                block.edgesToString(edges);
            }

            String nodesStr = "";
            if (nodes.size() < 0)
            {
                System.out.println("Trying to print empty function");
                System.exit(1);
            }
            nodesStr = String.join(";\n        ", nodes);
            nodesStr = "        " + nodesStr;
            nodesStr += ";\n";

            String edgesStr = "";
            if (edges.size() > 0)
            {
                edgesStr = String.join(";\n        ", edges);
                edgesStr = "        " + edgesStr;
                edgesStr += ";\n";
            }

            String dot =
                String.format(DOT_TOP_LEVEL, this.name, nodesStr, edgesStr);

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
