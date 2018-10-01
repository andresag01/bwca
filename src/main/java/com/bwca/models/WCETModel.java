package com.bwca.models;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;

public class WCETModel extends Model
{
    private Map<ISABlock, WCETBlockCost> blocks;
    private Map<BranchTarget, WCETEdgeCost> edges;

    public WCETModel()
    {
        blocks = new HashMap<ISABlock, WCETBlockCost>();
        edges = new HashMap<BranchTarget, WCETEdgeCost>();
    }

    public String getName()
    {
        return "wcet";
    }

    public String getBlockSummary(ISABlock block)
    {
        WCETBlockCost cost = blocks.get(block);
        return Integer.toString(cost.getPositiveCost());
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        String cost = getNegativeEdgeCost(edge);
        return (cost == null) ? cost : "-" + cost;
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        return Integer.toString(blocks.get(block).getPositiveCost());
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        int cost = edges.get(edge).getNegativeCost();
        return (cost == 0) ? null : Integer.toString(cost);
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        WCETBlockCost cost = blocks.get(block);

        if (cost == null)
        {
            cost = new WCETBlockCost();
            blocks.put(block, cost);
        }

        switch (inst.getInstruction())
        {
            case SEV:
                cost.addDirMem(2);
                break;

            case WFE:
                cost.addDirMem(1);
                cost.addMem(1);
                cost.addDir(1);
                break;

            case WFI:
                cost.addDirMem(2);
                break;

            case POP:
                cost.addDirMem(1);
                // Always assume the last load is a pointer so it needs marking
                cost.addDir(1);
                if (inst.getType() == InstructionType.BRANCH)
                {
                    cost.addAlu(2);
                    cost.addBranch(1);
                }
                cost.addDirMem(inst.getRegisterList().size());
                break;

            case LDMIA:
                cost.addDirMem(1);
                // Always assume the last load is a pointer so it needs marking
                cost.addDir(1);
                cost.addDirMem(inst.getRegisterList().size());
                break;

            case PUSH:
            case STMIA:
                cost.addDirMem(1);
                cost.addDirMem(inst.getRegisterList().size());
                break;

            case LDRB:
            case LDRH:
            case LDRSB:
            case LDRSH:
            case STRB:
                cost.addDirMem(1);
                cost.addMem(1);
                break;

            case LDR:
                cost.addDirMem(1);
                cost.addMem(1);
                cost.addDir(1);
                break;

            case STR:
                cost.addDirMem(2);
                break;

            case BLX:
                cost.addAlu(2);
                cost.addBranch(1);
                cost.addDir(1);
                break;

            case BL:
                cost.addAlu(3);
                cost.addBranch(1);
                break;

            case BX:
                cost.addAlu(2);
                cost.addBranch(1);
                cost.addDir(1);
                break;

            case B:
                cost.addAlu(2);
                cost.addBranch(1);
                break;

            case ADD:
            case SUB:
                if (inst.getType() == InstructionType.BRANCH)
                {
                    cost.addAlu(2);
                    cost.addBranch(1);
                }
                else
                {
                    cost.addAlu(1);
                }
                break;

            case CPY:
            case MOV:
                if (inst.getType() == InstructionType.BRANCH)
                {
                    cost.addAlu(2);
                    cost.addBranch(1);
                    cost.addDir(1);
                }
                else
                {
                    cost.addAlu(1);
                }
                break;

            case LSL:
            case ORR:
            case EOR:
            case NEG:
            case REV:
            case REV16:
            case REVSH:
            case MUL:
            case ROR:
            case SBC:
            case SXTB:
            case SXTH:
            case NOP:
            case TST:
            case UXTH:
            case UXTB:
            case MVN:
            case LSR:
            case CMN:
            case CMP:
            case BIC:
            case ASR:
            case AND:
            case ADC:
                cost.addAlu(1);
                break;

            case BKPT:
                cost.addAlu(1);
                break;

            case SVC:
                cost.addAlu(1);
                break;

            case CPS:
                cost.addAlu(1);
                break;
        }
    }

    public void addEdgeCost(BranchTarget edge)
    {
        WCETEdgeCost cost = edges.get(edge);

        if (edges.get(edge) == null)
        {
            cost = new WCETEdgeCost();
            edges.put(edge, cost);
        }

        // This edge reduces the cost of the block if it is the false
        // resolution of a branch
        if (edge.getCondition() != null && !edge.getCondition())
        {
            cost.subFalseBranch(2);
        }
    }
}
