package com.bwca.models.ihgc.wca;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;
import com.bwca.cfg.FunctionCallDetails;
import com.bwca.models.Model;

public class WCAModelIHGC extends Model
{
    private Map<ISABlock, WCABlockCostIHGC> blocks;
    private Map<FunctionCallDetails, Long> calls;

    public WCAModelIHGC()
    {
        this.blocks = new HashMap<ISABlock, WCABlockCostIHGC>();
        this.calls = new HashMap<FunctionCallDetails, Long>();
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
            System.out.println("Call cost not available in mode");
            System.exit(1);
        }

        cost.addFunctionCall(callCost);
    }

    public void addFunctionCallDetailsCost(FunctionCallDetails call,
                                           String cost)
    {
        calls.put(call, Long.parseLong(cost));
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
        WCABlockCostIHGC cost = blocks.get(block);

        if (cost == null)
        {
            cost = new WCABlockCostIHGC();
            blocks.put(block, cost);
        }

        if (inst.getInstruction() == Instruction.WFI)
        {
            cost.addAllocation(inst.getAllocationSize());
        }
    }
}
