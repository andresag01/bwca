package com.bwca.cfg;

import java.util.Arrays;
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
import java.io.File;
import java.io.FileWriter;
import java.io.BufferedWriter;
import java.io.IOException;

import com.bwca.models.Model;
import com.bwca.utils.PlatformUtils;

public class ISAFunction
{
    static final Pattern INST =
        Pattern.compile("^\\s+(?<address>[0-9a-fA-F]+):"
                        + "\\s+[0-9a-fA-F]{4}(\\s+[0-9a-fA-F]{4})?"
                        + "\\s+(?<opcode>\\w+)(\\.n)?(?<body>.*)$");
    static final Pattern FUNC = Pattern.compile("^(?<address>[0-9a-fA-F]+)\\s+"
                                                + "<(?<name>[^>]+)>:$");
    static final String DOT_TOP_LEVEL = "digraph G {\n"
        + "    subgraph cluster_cfg {\n"
        + "        color = white;\n"
        + "        node [shape=box,style=filled,fillcolor=yellow];\n"
        + "        label = \"Function: %s()%s\";\n"
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
        + "                    <td align=\"left\">Basic Block</td>\n"
        + "                    <td bgcolor=\"yellow\">    </td>\n"
        + "                </tr>\n"
        + "                <tr>\n"
        + "                    <td align=\"left\">Loop Block</td>\n"
        + "                    <td bgcolor=\"orange\">    </td>\n"
        + "                </tr>\n"
        + "                <tr>\n"
        + "                    <td align=\"left\">Loop Header</td>\n"
        + "                    <td bgcolor=\"red\">    </td>\n"
        + "                </tr>\n"
        + "            </table>>];\n"
        + "%s"
        + "%s"
        + "    }\n"
        + "}";
    static final String ILP_TOP_LEVEL = "/*\n"
        + " * ILP for:\n"
        + " *     - Function: %s@0x%08x\n"
        + " *     - Cost model: %s\n"
        + " */\n\n"
        + "/* Objective function */\n"
        + "%s: %s;\n\n"
        + "/* Output constraints */\n"
        + "%s\n"
        + "/* Input constraints */\n"
        + "%s\n"
        + "/* Loop constraints */\n"
        + "%s\n"
        + "/* Detailed block costs\n"
        + " *\n"
        + "%s*/\n\n"
        + "/* Block variable declarations */\n"
        + "%s\n"
        + "/* Edge variable declarations */\n"
        + "%s";

    static final String ILP_PROBLEM_FILE_EXT = ".lp";
    static final String ILP_SOLUTION_FILE_EXT = ".sol";

    static final String LP_SOLVE = "lp_solve";
    static final String[] LP_SOLVE_CMD = { LP_SOLVE };

    private Long address;
    private long size;
    private String name;
    private ISABlock entry;
    private ArrayList<ISABlock> blocks;
    private Set<String> infoMsgs;
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
        this.infoMsgs = new HashSet<String>();
        this.blocks = new ArrayList<ISABlock>();
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
        this.infoMsgs = new HashSet<String>();
        this.blocks = new ArrayList<ISABlock>();
        this.config = config;
        this.nextBlockId = 0;
        this.nextEdgeId = 0;
    }

    public String getName()
    {
        return name;
    }

    public LinkedList<String> getMissingInfoMessages()
    {
        LinkedList<String> msgs = new LinkedList<String>();

        for (ISABlock block : blocks)
        {
            msgs.addAll(block.getMissingInfoMessages());
        }

        msgs.addAll(infoMsgs);

        return msgs;
    }

    private int findStartOfFunctionInObjdump(ArrayList<String> objdump)
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
            if (name.indexOf(this.name) == -1)
            {
                // We use find() because sometimes the name in readelf is
                // cropped, so we need to check that name is a substring of
                // this.name instead of an identical match
                continue;
            }

            if (this.address != null && this.address != address)
            {
                // Not the correct function because even though the name in
                // readelf is a substring of this.name, but the address does
                // not match. Therefore, the substring matching is aliased to
                // another function
                continue;
            }

            // We need to check if the address is null because functions that
            // are in the config file do not have an address. Instead, we
            // discover the address when parsing the objdump
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

            // The readelf output sometimes has the function address clipped,
            // so make sure we use the function name from the objdump output
            this.name = name;

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

        return funcIndex;
    }

    private ArrayList<ISALine> extractInstructionsFromObjdump(
        ArrayList<String> objdump,
        int funcIndex,
        Map<String, SymbolTableRecord> symbolTable)
    {
        ArrayList<ISALine> insts = new ArrayList<ISALine>();

        // Parse the instructions
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
            ISALine line = new ISALine(address,
                                       opcode,
                                       body,
                                       config,
                                       this.address,
                                       this.size,
                                       symbolTable);
            insts.add(line);
        }

        return insts;
    }

    private void extractBranchDestinationAddresses(ArrayList<ISALine> insts,
                                                   Set<Long> branchTargetAddrs)
    {
        // Extract target addresses
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
                }
            }
        }
    }

    private Map<Long, ISABlock> groupInstructionsInBlocks(
        ArrayList<ISALine> insts,
        Set<Long> branchTargetAddrs)
    {
        // Create the blocks of the CFG
        Map<Long, ISABlock> blocksMap = new HashMap<Long, ISABlock>();
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

        return blocksMap;
    }

    private void createEdges(Map<Long, ISABlock> blocksMap, ISABlock exitBlock)
    {
        boolean hasExit = false;

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
                    if (inst.getInstruction() == Instruction.FUNC_EXIT)
                    {
                        // This is just a dummy block to consolidate all exists
                        // into a single point. Otherwise the solver cannot
                        // handle the ILP
                        break;
                    }
                    else if (inst.isExit())
                    {
                        // Link this block to the dummy exit block
                        blocks.get(i).addEdge(exitBlock);
                    }
                    else
                    {
                        // This is just a regular block that does not
                        // manipulate the pc, so fall through to the next block
                        if (blocks.size() == i + 1)
                        {
                            System.out.println("Trying to link last block in "
                                               + "a function with ith + 1 "
                                               + "block!");
                            System.exit(1);
                        }
                        blocks.get(i).addEdge(blocks.get(i + 1));
                    }
                    break;

                case BRANCH:
                case COND_BRANCH:
                    if (inst.isExit())
                    {
                        blocks.get(i).addEdge(exitBlock);
                        break;
                    }

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
                hasExit = true;
            }
        }

        if (!hasExit)
        {
            System.out.println("Function does not have exit block!\n");
            System.exit(1);
        }
    }

    private ISABlock addExitBlock()
    {
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
        blocks.add(dummyBlock);

        return dummyBlock;
    }

    public int parseInstructions(ArrayList<String> objdump,
                                 Map<String, SymbolTableRecord> symbolTable)
    {
        int funcIndex;
        ArrayList<ISALine> insts;
        Set<Long> branchTargetAddrs;
        Map<Long, ISABlock> blocksMap;
        ISABlock exitBlock;

        if (size == 0)
        {
            // This is only a place holder block. Do nothing...
            return 0;
        }

        funcIndex = findStartOfFunctionInObjdump(objdump);
        insts =
            extractInstructionsFromObjdump(objdump, funcIndex, symbolTable);
        branchTargetAddrs = new HashSet<Long>();
        extractBranchDestinationAddresses(insts, branchTargetAddrs);
        blocksMap = groupInstructionsInBlocks(insts, branchTargetAddrs);

        entry = blocks.get(0);

        exitBlock = addExitBlock();
        createEdges(blocksMap, exitBlock);

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
        if (blocks.size() == 0)
        {
            // This is just a placeholder ISAFunction. Nothing to do...
            return;
        }
        garbageCollectBlocks();
        detectLoops();
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

        // Label loop headers
        traverseInDFS(entry, 1);

        // Set loop depth
        for (ISABlock block : blocks)
        {
            block.setMark(false);
        }
        findLoopDepth(entry);
    }

    private void findLoopDepth(ISABlock block)
    {
        block.setMark(true);

        if (block.isLoopHeader())
        {
            if (block.getInnerLoopHeader() == null)
            {
                block.setLoopDepth(0);
            }
            else
            {
                block.setLoopDepth(block.getInnerLoopHeader().getLoopDepth() +
                                   1);
            }
        }
        else if (block.getInnerLoopHeader() != null)
        {
            block.setLoopDepth(block.getInnerLoopHeader().getLoopDepth());
        }

        for (BranchTarget target : block.getEdges())
        {
            ISABlock successor = target.getBlock();
            if (successor.isMarked())
            {
                continue;
            }
            findLoopDepth(successor);
        }
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
                        System.out.printf("Function %s has an irreducible "
                                              + "loop!",
                                          name);
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

    public List<ISABlock> getBlocks()
    {
        return blocks;
    }

    public void applyModel(String outputDir,
                           Model model,
                           FunctionCallDetails call)
    {
        String baseFilename;
        String lpFile;
        String solFile;
        CFGSolution solution;

        baseFilename = String.format("%s%s%s@0x%08x",
                                     outputDir,
                                     File.separator,
                                     model.getName(),
                                     call.getCallAddress());
        lpFile = baseFilename + ILP_PROBLEM_FILE_EXT;
        solFile = baseFilename + ILP_SOLUTION_FILE_EXT;

        for (ISABlock block : blocks)
        {
            // Add the cost of the blocks and edges
            block.applyModel(model);

            // Add any other miscelaneous cost for the block
            model.addBlockCost(block, call);

            // Add the cost of the function calls this block makes
            for (FunctionCallDetails dep : block.getFunctionCallDependencies())
            {
                model.addFunctionCallCost(block, dep);
            }
        }

        // Generate and solve the ILP for the function
        writeILP(lpFile, model, call);
        solution = solveILP(lpFile, solFile);

        // Add the solution for this function call for later use
        model.addFunctionCallDetailsCost(this, call, solution);

        // Write the annotated CFG in dot format
        writeDotFile(baseFilename + ".dot", model, call);

        // Clear the model's data structures so that we can accurately resolve
        // another call later without stacking the weights of blocks and edges
        model.clear();
    }

    private CFGSolution solveILP(String lpFile, String solFile)
    {
        ArrayList<String> output = null;
        File outputLpSolveFile;
        String[] cmd;

        // Run the lp_solve utility with the program statement as an input
        try
        {
            outputLpSolveFile = new File(solFile);
            cmd = Arrays.copyOf(LP_SOLVE_CMD, LP_SOLVE_CMD.length + 1);
            cmd[cmd.length - 1] = lpFile;
            output = PlatformUtils.runShell(cmd, outputLpSolveFile);
        }
        catch (IOException ioe)
        {
            ioe.printStackTrace();
            System.out.println(ioe);
            System.exit(1);
        }
        catch (InterruptedException ie)
        {
            ie.printStackTrace();
            System.out.println(ie);
            System.exit(1);
        }

        return new CFGSolution(output);
    }

    public void checkMissingInformation(FunctionCallDetails call)
    {
        for (ISABlock block : blocks)
        {
            for (BranchTarget edge : block.getEdges())
            {
                ISABlock successor = edge.getBlock();
                long successorAddress = successor.getFirstLine().getAddress();

                if (!successor.isLoopHeader())
                {
                    continue;
                }
                else if (block.getInnerLoopHeader() == successor)
                {
                    continue;
                }
                else if (block == successor)
                {
                    continue;
                }

                // This successor is the header of a loop and needs a bound
                ISALine inst = block.getFirstLine();

                // Check if we have information about this bound in the config
                LoopBound bound = config.getLoopBounds(call.getCallAddress(),
                                                       successorAddress);
                if (bound == null)
                {
                    String msg = String.format("loopbound 0x%08x min <BOUND> "
                                                   + "max <BOUND> from call "
                                                   + "0x%08x",
                                               successorAddress,
                                               call.getCallAddress());
                    infoMsgs.add(msg);
                }
            }

            // TODO: Skip over this check if we are not applying the relevant
            // cost mode (e.g. WCA)
            for (ISALine inst : block.getInstructions())
            {
                if (inst.getInstruction() == Instruction.WFI)
                {
                    long instAddress = inst.getAddress();
                    long callAddress = call.getCallAddress();
                    Long allocSize =
                        config.getAllocationSize(callAddress, instAddress);

                    if (allocSize == null)
                    {
                        String msg = String.format("allocation 0x%08x <BOUND> "
                                                       + "from call 0x%08x",
                                                   instAddress,
                                                   callAddress);
                        infoMsgs.add(msg);
                    }
                }
            }
        }
    }

    private void writeILP(String filename,
                          Model model,
                          FunctionCallDetails call)
    {
        StringBuilder outConstraints = new StringBuilder();
        StringBuilder inConstraints = new StringBuilder();
        StringBuilder loopConstraints = new StringBuilder();
        StringBuilder blockDecls = new StringBuilder();
        StringBuilder edgeDecls = new StringBuilder();
        StringBuilder blockCosts = new StringBuilder();

        String blockPfix = "b";
        String edgePfix = "e";

        String ilpFunctionType = model.getObjectiveFunctionType();

        // Make the string for the problem
        List<String> problemSub = new LinkedList<String>();
        List<String> problemAdd = new LinkedList<String>();
        for (ISABlock block : blocks)
        {
            // Add the block cost to the top level formula
            String positiveCost = model.getPositiveBlockCost(block);
            String negativeCost = model.getNegativeBlockCost(block);

            if (positiveCost != null)
            {
                positiveCost = String.format(
                    "%s %s%d", positiveCost, blockPfix, block.getId());
                problemAdd.add(positiveCost);
            }
            if (negativeCost != null)
            {
                negativeCost = String.format(
                    "%s %s%d", negativeCost, blockPfix, block.getId());
                problemSub.add(negativeCost);
            }

            for (BranchTarget edge : block.getEdges())
            {
                positiveCost = model.getPositiveEdgeCost(edge);
                negativeCost = model.getNegativeEdgeCost(edge);

                if (positiveCost != null)
                {
                    positiveCost = String.format(
                        "%s %s%d", positiveCost, edgePfix, edge.getId());
                    problemAdd.add(positiveCost);
                }
                if (negativeCost != null)
                {
                    negativeCost = String.format(
                        "%s %s%d", negativeCost, edgePfix, edge.getId());
                    problemSub.add(negativeCost);
                }
            }
        }
        String intercept = model.getInterceptCost();
        if (problemAdd.size() < 1)
        {
            System.out.println("Objective function does not have additive "
                               + "components!");
            System.exit(1);
        }
        String problemStr = String.join("\n     + ", problemAdd);
        if (problemSub.size() > 0)
        {
            problemStr += "\n     - " + String.join("\n     - ", problemSub);
        }
        problemStr += (intercept != null) ? "\n    " + intercept : "";

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
                                       + "exit in function " + name + "\n");
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
            for (BranchTarget edge : block.getEdges())
            {
                ISABlock successor = edge.getBlock();
                long successorAddress = successor.getFirstLine().getAddress();

                if (!successor.isLoopHeader())
                {
                    continue;
                }
                else if (block.getInnerLoopHeader() == successor)
                {
                    continue;
                }
                else if (block == successor)
                {
                    continue;
                }

                // This successor is the header of a loop and needs a bound
                ISALine inst = block.getFirstLine();
                String lbound = "BOUND";
                String ubound = "BOUND";

                // Check if we have information about this bound in the config
                LoopBound bound = config.getLoopBounds(call.getCallAddress(),
                                                       successorAddress);
                if (bound != null)
                {
                    lbound = Long.toString(bound.getLowerBound());
                    ubound = Long.toString(bound.getUpperBound());
                }
                else
                {
                    System.out.printf("No information about loop at 0x%08x\n",
                                      inst.getAddress());
                }

                String constraint = String.format("\n/* Header 0x%08x */\n"
                                                      + "%s %s%d <= %s%d;\n",
                                                  inst.getAddress(),
                                                  lbound,
                                                  blockPfix,
                                                  block.getId(),
                                                  blockPfix,
                                                  successor.getId());
                loopConstraints.append(constraint);

                constraint = String.format("%s%d <= %s %s%d;\n",
                                           blockPfix,
                                           successor.getId(),
                                           ubound,
                                           blockPfix,
                                           block.getId());
                loopConstraints.append(constraint);
            }
        }

        // Block and edge declarations (and block cost breakdown)
        for (ISABlock block : blocks)
        {
            blockDecls.append(
                String.format("int %s%d;\n", blockPfix, block.getId()));

            if (blockCosts.length() > 0)
            {
                blockCosts.append(" *\n");
            }
            blockCosts.append(model.getBlockDetails(block));

            for (BranchTarget edge : block.getEdges())
            {
                edgeDecls.append(
                    String.format("int %s%d;\n", edgePfix, edge.getId()));
            }
        }

        try
        {
            // Write the data in ILP format
            FileWriter fwriter = new FileWriter(filename);
            BufferedWriter bwriter = new BufferedWriter(fwriter);

            String output = String.format(ILP_TOP_LEVEL,
                                          name,
                                          call.getCallAddress(),
                                          model.getName(),
                                          ilpFunctionType,
                                          problemStr,
                                          outConstraints.toString(),
                                          inConstraints.toString(),
                                          loopConstraints.toString(),
                                          blockCosts.toString(),
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

    public void buildFunctionCallDependencyList()
    {
        for (ISABlock block : blocks)
        {
            block.buildFunctionCallDependencyList();
        }
    }

    public Set<String> getFunctionCallDependencyNames()
    {
        Set<String> deps = new HashSet<String>();

        for (ISABlock block : blocks)
        {
            for (FunctionCallDetails call :
                 block.getFunctionCallDependencies())
            {
                deps.add(call.getCalleeName());
            }
        }

        return deps;
    }

    public List<FunctionCallDetails> getFunctionCallDependencies()
    {
        List<FunctionCallDetails> deps = new LinkedList<FunctionCallDetails>();

        for (ISABlock block : blocks)
        {
            deps.addAll(block.getFunctionCallDependencies());
        }

        return deps;
    }

    public void writeDotFile(String filename,
                             Model model,
                             FunctionCallDetails call)
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

            // This ISAFunction might be empty because we do not have
            // information about its size
            String nodesStr = "";
            if (nodes.size() > 0)
            {
                nodesStr = String.join(";\n        ", nodes);
                nodesStr = "        " + nodesStr;
                nodesStr += ";\n";
            }

            String edgesStr = "";
            if (edges.size() > 0)
            {
                edgesStr = String.join(";\n        ", edges);
                edgesStr = "        " + edgesStr;
                edgesStr += ";\n";
            }

            String functionCallCost = "";
            if (model != null && call != null)
            {
                functionCallCost = String.format(
                    " {cost:%s}", model.getFunctionCallCost(call));
            }

            String dot = String.format(DOT_TOP_LEVEL,
                                       this.name,
                                       functionCallCost,
                                       nodesStr,
                                       edgesStr);

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
