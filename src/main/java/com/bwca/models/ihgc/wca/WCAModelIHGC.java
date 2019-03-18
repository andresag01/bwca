package com.bwca.models.ihgc.wca;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.ISAFunction;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;
import com.bwca.cfg.FunctionCallDetails;
import com.bwca.cfg.CFGSolution;
import com.bwca.cfg.CFGConfiguration;
import com.bwca.models.Model;

public class WCAModelIHGC extends Model
{
    private Map<ISABlock, WCABlockCostIHGC> blocks;
    private Map<FunctionCallDetails, Long> calls;
    private CFGConfiguration config;

    public WCAModelIHGC(CFGConfiguration config)
    {
        this.blocks = new HashMap<ISABlock, WCABlockCostIHGC>();
        this.calls = new HashMap<FunctionCallDetails, Long>();
        this.config = config;
    }

    public void clear()
    {
        blocks = new HashMap<ISABlock, WCABlockCostIHGC>();
    }

    public String getName()
    {
        return "wca_ihgc";
    }

    public String getBlockSummary(ISABlock block)
    {
        WCABlockCostIHGC cost = blocks.get(block);
        return Long.toString(cost.getPositiveCost());
    }

    public String getBlockDetails(ISABlock block)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format(" * %s%d:\n", "b", block.getId()));
        builder.append(blocks.get(block).toString());

        for (FunctionCallDetails call : block.getFunctionCallDependencies())
        {
            String callDetails = String.format(" *        - %s@0x%08x: %d\n",
                                               call.getCalleeName(),
                                               call.getCallAddress(),
                                               calls.get(call));
            builder.append(callDetails);
        }

        return builder.toString();
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        return null;
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        return Long.toString(blocks.get(block).getPositiveCost());
    }

    public String getNegativeBlockCost(ISABlock block)
    {
        return null;
    }

    public String getPositiveEdgeCost(BranchTarget edge)
    {
        return null;
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        return null;
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addFunctionCallCost(ISABlock block, FunctionCallDetails call)
    {
        WCABlockCostIHGC cost = blocks.get(block);
        Long callCost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Block is not in model when adding function "
                               + "call cost!");
            System.exit(1);
        }
        if (callCost == null)
        {
            System.out.println("Call cost not available in model");
            System.exit(1);
        }

        // Add the cost of functions called within the block
        cost.addFunctionCall(callCost);
    }

    public void addBlockCost(ISABlock block, FunctionCallDetails call)
    {
        WCABlockCostIHGC cost = new WCABlockCostIHGC();
        blocks.put(block, cost);

        // Add the cost of getm instructions executed within the block
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
                    System.out.printf("No information about allocation at "
                                          + "0x%08x from call 0x%08x",
                                      instAddress,
                                      callAddress);
                }
                else
                {
                    cost.addFunctionCall(allocSize);
                }
            }
        }
    }

    public void addFunctionCallDetailsCost(ISAFunction caller,
                                           FunctionCallDetails call,
                                           CFGSolution cost)
    {
        // Apparently, lp_solve can output a result as a floating-point value
        // even though everything is an integer. To get around this, we parse
        // the input solution as a double and check the parse value is below
        // a threshold
        double fpCost =
            Double.parseDouble(cost.getObjectiveFunctionSolution());
        double floor = Math.floor(fpCost);

        if (fpCost - floor > Model.FP_THRESHOLD)
        {
            System.out.println("Floating-poing value above threshold");
            System.exit(1);
        }

        calls.put(call, (long)floor);
    }

    public String getObjectiveFunctionType()
    {
        return "max";
    }

    public String getFunctionCallCost(FunctionCallDetails call)
    {
        Long cost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Function call not registered with mode!\n");
            System.exit(1);
        }

        return cost.toString();
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
    {
        return;
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        return;
    }
}
