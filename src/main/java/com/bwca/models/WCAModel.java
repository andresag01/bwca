package com.bwca.models;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;

public class WCAModel extends Model
{
    private Map<ISABlock, Integer> wfi;
    private Map<ISABlock, Integer> malloc;

    public WCAModel()
    {
        wfi = new HashMap<ISABlock, Integer>();
        malloc = new HashMap<ISABlock, Integer>();
    }

    public String getName()
    {
        return "wca";
    }

    public String getBlockSummary(ISABlock block)
    {
        return String.format("w=%d,m=%d", wfi.get(block), malloc.get(block));
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        return null;
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        StringBuilder builder = new StringBuilder();

        for (int i = 0; i < wfi.get(block); i++)
        {
            builder.append("BOUND_WFI" + i);
            builder.append((i + 1 >= wfi.get(block)) ? "" : " +\n    ");
        }

        for (int i = 0; i < malloc.get(block); i++)
        {
            builder.append(" +\n    " + "BOUND_MALLOC" + i);
        }

        return (builder.length() == 0) ? "0" : builder.toString();
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        return null;
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        Integer wfiCost = wfi.get(block);
        Integer mallocCost = malloc.get(block);;

        if (wfiCost == null)
        {
            wfiCost = 0;
            mallocCost = 0;
        }

        if (inst.getInstruction() == Instruction.WFI)
        {
            // GETM instruction
            wfiCost += 1;
        }
        else if (inst.getType() == InstructionType.BRANCH_LINK)
        {
            String targetFunc = inst.getTargetFunction();
            if (targetFunc != null &&
                (targetFunc.contains("malloc") ||
                 targetFunc.contains("calloc") ||
                 targetFunc.contains("realloc")))
            {
                mallocCost += 1;
            }
        }

        wfi.put(block, wfiCost);
        malloc.put(block, mallocCost);
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
    {
        return;
    }
}
