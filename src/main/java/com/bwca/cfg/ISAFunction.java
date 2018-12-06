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
        + "    style=filled;\n"
        + "    node [shape=box,style=filled,fillcolor=yellow];\n"
        + "    label = \"%s\";\n"
        + "    labelloc = \"t\";\n"
        + "%s"
        + "%s"
        + "}";
    static final String ILP_TOP_LEVEL = "/* ILP for function %s */\n\n"
        + "/* Problem */\n"
        + "max: %s;\n\n"
        + "/* Block weights */\n"
        + "%s\n"
        + "/* Function call weights */\n"
        + "%s\n"
        + "/* Output constraints */\n"
        + "%s\n"
        + "/* Input constraints */\n"
        + "%s\n"
        + "/* Loop constraints */\n"
        + "%s\n"
        + "/* Block variable declarations */\n"
        + "%s\n"
        + "/* Edge variable declarations */\n"
        + "%s";

    private Long address;
    private long size;
    private String name;
    private ISABlock entry;
    private ArrayList<ISABlock> blocks;
    private LinkedList<String> infoMsgs;
    private CFGConfiguration config;

    private int nextEdgeId;
    private int nextBlockId;

    public ISAFunction(long address,
                       long size,
                       String name,
                       CFGConfiguration config)
    {
        // Clear the bottom bit of the address because this is thumb
        this.address = address & ~0x1;
        this.size = size;
        this.name = name;
        this.entry = null;
        this.infoMsgs = new LinkedList<String>();
        this.config = config;
        this.nextBlockId = 0;
        this.nextEdgeId = 0;
    }

    public ISAFunction(long size, String name, CFGConfiguration config)
    {
        this.address = null;
        this.size = size;
        this.name = name;
        this.entry = null;
        this.infoMsgs = new LinkedList<String>();
        this.config = config;
        this.nextBlockId = 0;
        this.nextEdgeId = 0;
    }

    public LinkedList<String> getMissingInfoMessages()
    {
        return infoMsgs;
    }

    public int parseInstructions(ArrayList<String> objdump)
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

            if (this.address == null)
            {
                this.address = address;
            }
            else if (address != this.address)
            {
                // Something went wrong
                System.out.printf("Function at 0x%08X does not match address "
                                      + "at symbol table 0x%08X for function "
                                      + "%s\n",
                                  address,
                                  this.address,
                                  name);
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
            ISALine line = new ISALine(address, opcode, body, config);
            // Check for missing information
            insts.add(line);
            infoMsgs.addAll(line.getMissingInfoMessages());
        }

        // Extract target addresses
        Set<Long> branchTargetAddrs = new HashSet<Long>();
        Map<Long, String> outOfFuncBranchTargetAddrs =
            new HashMap<Long, String>();
        for (ISALine inst : insts)
        {
            if (inst.getType() == InstructionType.BRANCH ||
                inst.getType() == InstructionType.COND_BRANCH)
            {
                for (BranchTarget target : inst.getBranchTargets())
                {
                    Long destAddress = target.getAddress();
                    if (destAddress != null && destAddress >= this.address &&
                        destAddress < this.address + this.size)
                    {
                        // Add it do the map if the target is in the function
                        branchTargetAddrs.add(destAddress);
                    }
                    else
                    {
                        if (destAddress != inst.getTargetFunctionAddress() ||
                            inst.getTargetFunction() == null)
                        {
                            System.out.println("Unconditional function call "
                                               + "does not have information");
                            System.exit(1);
                        }
                        outOfFuncBranchTargetAddrs.put(destAddress,
                                                    inst.getTargetFunction());
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
            if (inst.getType() == InstructionType.BRANCH ||
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

        // Create dummy blocks for the target addresses that are outside the
        // function
        for (Map.Entry<Long, String> entry :
             outOfFuncBranchTargetAddrs.entrySet())
        {
            ISABlock dummyBlock = new ISABlock(entry.getKey(), nextBlockId++);
            blocks.add(dummyBlock);
            blocksMap.put(entry.getKey(), dummyBlock);
            dummyBlock.setExit(true);

            // Add a fake instruction that represents a function call
            dummyBlock.addLine(new ISALine(entry.getKey(),
                                           "func_call",
                                           entry.getValue(),
                                           true,
                                           entry.getValue(),
                                           entry.getKey(),
                                           Instruction.FUNC_CALL,
                                           InstructionType.OTHER));
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
            blocks.get(i).setEdges(inst.getBranchTargets());
            switch (inst.getType())
            {
                case OTHER:
                case BRANCH_LINK:
                    if (i + 1 >= blocks.size())
                    {
                        // There are no subsequent blocks, this is probably an
                        // exit block, but at the momment the user has to
                        // label these manually

                        if (inst.getOpcode().equals("nop"))
                        {
                            // This is almost certainly a padding instruction
                            // that the compiler added for alignment purposes.
                            // These are generally nops and do not count in the
                            // CFG. Simply leave this block unconnected to the
                            // rest of the graph and it will be cleaned up
                            // later
                            break;
                        }
                        else if (name.equals("success") &&
                                 inst.getOpcode().equals("bkpt"))
                        {
                            // This is the "special" success() function that
                            // terminates in a bkpt instruction and indicates
                            // success for the simulator
                            break;
                        }
                        else if (name.equals("abort") &&
                                 inst.getOpcode().equals("svc"))
                        {
                            // This is the "special" abort() function that
                            // terminates in a bkpt instruction and indicates
                            // failure for the simulator
                            break;
                        }
                        else if (inst.getInstruction() ==
                                 Instruction.FUNC_CALL)
                        {
                            // This is a special dummy block for unconditional
                            // branches to functions
                            break;
                        }

                        // This situation is rather dodgy because functions
                        // normally return on a proper branch instruction (to
                        // the lr register or an appropriate value). At the
                        // moment we fail
                        System.out.println("Last block of function '" + name +
                                           "' terminates in unexpected " +
                                           "(non-branch) instruction");
                        System.out.println(blocks.get(i));
                        System.exit(1);
                    }

                    // This is just a regular block that does not manipulate
                    // the pc, so fall through to the next block
                    blocks.get(i).addEdge(blocks.get(i + 1));
                    break;

                case BRANCH:
                case COND_BRANCH:
                    for (BranchTarget target : inst.getBranchTargets())
                    {
                        Long address = target.getAddress();
                        target.setBlock(blocksMap.get(address));
                    }
                    break;

                default:
                    System.out.println("Invalid instruction type");
                    System.exit(1);
            }

            if (inst.isExit())
            {
                blocks.get(i).setExit(true);
            }
        }

        // Create a dummy block to consolidate all exit points into a single
        // one. Otherwise lp_solve cannot deal with the problem
        ISABlock dummyBlock = new ISABlock(0, nextBlockId++);
        dummyBlock.setExit(true);
        dummyBlock.addLine(new ISALine(0,
                                       "func_exit",
                                       "",
                                       true,
                                       null,
                                       0,
                                       Instruction.FUNC_EXIT,
                                       InstructionType.OTHER));
        dummyBlock.setEdges(dummyBlock.getLastLine().getBranchTargets());

        boolean hasExit = false;
        for (int i = 0; i < blocks.size(); i++)
        {
            if (!blocks.get(i).isExit())
            {
                continue;
            }

            if (blocks.get(i).getEdges().size() != 0)
            {
                System.out.println("Exit block has outgoing edges in "
                                   + "function " + name);
                System.exit(1);
            }

            hasExit = true;

            // Make the exit block point to the single exit point
            blocks.get(i).addEdge(dummyBlock);
        }
        blocks.add(dummyBlock);


        // Check that there is at least one exit block and that no exit block
        // has outgoing edges
        if (!hasExit)
        {
            infoMsgs.add("No exit block");
        }

        if (infoMsgs.size() != 0)
        {
            return -1;
        }

        return 0;
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
            block.setIntermediateLoopBranch(false);
            block.setLoopBranch(false);
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
                if (successor.isIntermediateLoopBranch())
                {
                    if (block.getLastLine().getType() !=
                        InstructionType.COND_BRANCH)
                    {
                        // This can happen if there are two intermediate blocks
                        // my suspicion is that we just need to tag this block
                        // as an intermediateLoopBranch and recurse back until
                        // we find the conditional branch block. However, I
                        // have not found a program that has this structure in
                        // the code yet and I am not sure it works
                        System.out.println("Failed to identify loop branch " +
                            "block\n");
                        System.exit(1);
                    }
                    block.setLoopBranch(true);
                }
                tagLoopHeader(block, nh);
            }
            else
            {
                if (successor.getDFSPosition() > 0)
                {
                    // Case B
                    successor.setLoopHeader(true);
                    // We found the block that loops back to the beginning of
                    // the next iteration. The problem is that the current
                    // block can either be the conditional branch or some other
                    // intermediate block (e.g. the branch back is too long and
                    // the compiler put in an unconditional branch in between).
                    // To get around this problem, we check this block ends in
                    // a conditional branch. If this is the case, then we know
                    // this is the correct branch block of the loop. Otherwise,
                    // we label the block as intermediate and let the previous
                    // recursive calls identify which one of them has the
                    // correct branch block (check case A)
                    if (block.getLastLine().getType() ==
                        InstructionType.COND_BRANCH)
                    {
                        block.setLoopBranch(true);
                    }
                    else
                    {
                        block.setIntermediateLoopBranch(true);
                    }
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

    private class FunctionCallInformation
    {
        public int callCount;
        public long targetAddress;

        public FunctionCallInformation(int callCount,
                                       long targetAddress)
        {
            this.callCount = callCount;
            this.targetAddress = targetAddress;
        }
    };

    private Map<String, FunctionCallInformation> getFunctionCalls(
                                                                ISABlock block)
    {
        Map<String, FunctionCallInformation> callInfo =
                                new HashMap<String, FunctionCallInformation>();
        FunctionCallInformation info;
        String functionName;
        long functionAddress;

        for (ISALine inst : block.getInstructions())
        {
            functionName = inst.getTargetFunction();

            if (functionName == null)
            {
                continue;
            }
            else if (inst.getType() == InstructionType.BRANCH ||
                     inst.getType() == InstructionType.COND_BRANCH)
            {
                // This is handled via the dummy block, so skip it
                continue;
            }

            if (inst.getType() != InstructionType.BRANCH_LINK &&
                inst.getType() != InstructionType.OTHER &&
                inst.getInstruction() == Instruction.FUNC_CALL)
            {
                System.out.println("Instruction has function call but is of "
                                   + "unexpected type");
                System.exit(1);
            }

            functionAddress = inst.getTargetFunctionAddress();

            if (callInfo.containsKey(functionName))
            {
                info = callInfo.get(functionName);
                info.callCount++;
            }
            else
            {
                info = new FunctionCallInformation(1, functionAddress);
                callInfo.put(functionName, info);
            }
        }

        return callInfo;
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
            StringBuilder blockWeights = new StringBuilder();
            StringBuilder functionWeights = new StringBuilder();
            StringBuilder blockDecls = new StringBuilder();
            StringBuilder edgeDecls = new StringBuilder();
            Map<String, FunctionCallInformation> callInfo;
            Set<String> functionsCalled = new HashSet<String>();

            String blockPfix = model.getName() + "_b";
            String weightPfix = model.getName() + "_wb";
            String edgePfix = model.getName() + "_e";

            // Make the string for the problem
            List<String> problem = new LinkedList<String>();
            for (ISABlock block : blocks)
            {
                StringBuilder blockCost = new StringBuilder();
                callInfo = getFunctionCalls(block);

                // Add the block cost to the top level formula
                blockCost.append(weightPfix + block.getId() + " " +
                                 blockPfix + block.getId());
                for (BranchTarget edge : block.getEdges())
                {
                    String neg = model.getNegativeEdgeCost(edge);
                    if (neg != null)
                    {
                        blockCost.append(" - " + neg + " " + edgePfix +
                                         edge.getId());
                    }
                }

                // Add to the block weight variable
                blockWeights.append(
                    String.format("%s%d = %s",
                                  weightPfix,
                                  block.getId(),
                                  model.getPositiveBlockCost(block)));
                for (Map.Entry<String, FunctionCallInformation> entry :
                     callInfo.entrySet())
                {
                    blockWeights.append(" + ");
                    blockWeights.append(entry.getKey());
                    blockWeights.append(" ");
                    blockWeights.append(entry.getValue().callCount);
                }
                blockWeights.append(";\n");

                functionsCalled.addAll(callInfo.keySet());
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
                outConstraints.append(blockPfix + block.getId() + " = ");
                if (block.getEdges().size() == 0)
                {
                    if (block.getLastLine().getInstruction() !=
                        Instruction.FUNC_EXIT)
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
                    outEdges.add(edgePfix + edge.getId());

                    ISABlock target = edge.getBlock();
                    List<String> inEdgeList = inEdges.get(target);
                    if (inEdgeList == null)
                    {
                        inEdgeList = new LinkedList<String>();
                        inEdges.put(target, inEdgeList);
                    }
                    inEdgeList.add(edgePfix + edge.getId());
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

                inConstraints.append(blockPfix + block.getId() + " = ");

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
                            ISALine inst = block.getLastLine();
                            String lbound = "BOUND";
                            String ubound = "BOUND";

                            // Check if we have information about this bound in
                            // the config
                            Map<Long, LoopBound> loops =
                                config.getLoopBounds();

                            if (loops.containsKey(inst.getAddress()))
                            {
                                LoopBound bound = loops.get(inst.getAddress());
                                lbound = Long.toString(bound.getLowerBound());
                                ubound = Long.toString(bound.getUpperBound());
                            }
                            else
                            {
                                System.out.printf("No information about loop "
                                                  + "at 0x%08x\n",
                                                  inst.getAddress());
                            }

                            String constraint =
                                String.format("/* Branch at 0x%08x */\n" +
                                              "%s%d >= %s %s%d;\n",
                                              inst.getAddress(),
                                              blockPfix,
                                              block.getId(),
                                              lbound,
                                              edgePfix,
                                              edge.getId());
                            loopConstraints.append(constraint);

                            constraint = String.format("%s%d <= %s %s%d;\n",
                                                       blockPfix,
                                                       block.getId(),
                                                       ubound,
                                                       edgePfix,
                                                       edge.getId());
                            loopConstraints.append(constraint);
                        }
                    }
                }
            }

            // Function weights
            for (String function : functionsCalled)
            {
                functionWeights.append(function + " = WEIGHT;\n");
            }

            // Block and edge declarations
            for (ISABlock block : blocks)
            {
                blockDecls.append(String.format("int %s%d;\n",
                                                blockPfix,
                                                block.getId()));

                for (BranchTarget edge : block.getEdges())
                {
                    edgeDecls.append(String.format("int %s%d;\n",
                                                   edgePfix,
                                                   edge.getId()));
                }
            }

            // Write the data in ILP format
            String output = String.format(ILP_TOP_LEVEL,
                                          name,
                                          problemStr,
                                          blockWeights.toString(),
                                          functionWeights.toString(),
                                          outConstraints.toString(),
                                          inConstraints.toString(),
                                          loopConstraints.toString(),
                                          blockDecls.toString(),
                                          edgeDecls.toString());
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
                block.edgesToString(edges, model);
            }

            String nodesStr = "";
            if (nodes.size() < 0)
            {
                System.out.println("Trying to print empty function");
                System.exit(1);
            }
            nodesStr = String.join(";\n    ", nodes);
            nodesStr = "    " + nodesStr;
            nodesStr += ";\n";

            String edgesStr = "";
            if (edges.size() > 0)
            {
                edgesStr = String.join(";\n    ", edges);
                edgesStr = "    " + edgesStr;
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
