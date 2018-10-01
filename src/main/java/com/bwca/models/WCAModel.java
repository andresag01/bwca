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
    private Map<ISABlock, Integer> blocks;

    public WCAModel()
    {
        blocks = new HashMap<ISABlock, Integer>();
    }

    public String getName()
    {
        return "wca";
    }

    public String getBlockSummary(ISABlock block)
    {
        Integer cost = blocks.get(block);
        return cost.toString();
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        return null;
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        return blocks.get(block).toString();
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
        Integer cost = blocks.get(block);

        if (cost == null)
        {
            cost = 0;
        }

        if (inst.getInstruction() == Instruction.WFI)
        {
            // GETM instruction
            cost += 1;
        }
        else if (inst.getType() == InstructionType.BRANCH_LINK)
        {
            String targetFunc = inst.getTargetFunction();
            if (targetFunc != null &&
                (targetFunc.contains("malloc") ||
                 targetFunc.contains("calloc") ||
                 targetFunc.contains("realloc")))
            {
                cost += 1;
            }
        }

        blocks.put(block, cost);
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
    {
        return;
    }
}
