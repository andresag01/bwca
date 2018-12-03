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
    private Map<ISABlock, Long> wfi;
    private Map<ISABlock, Long> malloc;

    public WCAModel()
    {
        wfi = new HashMap<ISABlock, Long>();
        malloc = new HashMap<ISABlock, Long>();
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

        builder.append(wfi.get(block) + " + " + malloc.get(block));

        return builder.toString();
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
        Long wfiCost = wfi.get(block);
        Long mallocCost = malloc.get(block);;

        if (wfiCost == null)
        {
            wfiCost = 0L;
            mallocCost = 0L;
        }

        if (inst.getInstruction() == Instruction.WFI)
        {
            // GETM instruction
            wfiCost += inst.getAllocationSize();
        }
        else
        {
            String targetFunc = inst.getTargetFunction();
            if (targetFunc != null &&
                (targetFunc.contains("malloc") ||
                 targetFunc.contains("calloc") ||
                 targetFunc.contains("realloc")))
            {
                switch (inst.getType())
                {
                    case BRANCH_LINK:
                        mallocCost += inst.getAllocationSize();
                        break;

                    default:
                        System.out.println("Branching to malloc, realloc or "
                                           + "calloc without link");
                        System.exit(1);
                }
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
