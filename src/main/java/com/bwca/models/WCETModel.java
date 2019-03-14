package com.bwca.models;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;
import com.bwca.cfg.FunctionCallDetails;

public class WCETModel extends Model
{
    private Map<ISABlock, WCETBlockCost> blocks;
    private Map<BranchTarget, WCETEdgeCost> edges;
    private Map<FunctionCallDetails, Integer> calls;

    public WCETModel()
    {
        blocks = new HashMap<ISABlock, WCETBlockCost>();
        edges = new HashMap<BranchTarget, WCETEdgeCost>();
        calls = new HashMap<FunctionCallDetails, Integer>();
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

    public void addFunctionCallCost(ISABlock block, FunctionCallDetails call)
    {
        WCETBlockCost cost = blocks.get(block);
        Integer callCost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Block is not in model when adding function " +
                               "call cost!");
            System.exit(1);
        }
        if (callCost == null)
        {
            System.out.println("Call cost not available in model");
            System.exit(1);
        }

        cost.addFunctionCall(callCost);
    }

    public void addFunctionCallDetailsCost(FunctionCallDetails call,
                                           String cost)
    {
        calls.put(call, Integer.parseInt(cost));
    }

    public String getObjectiveFunctionType()
    {
        return "max";
    }

    public String getFunctionCallCost(FunctionCallDetails call)
    {
        Integer cost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Function call not registered with model!\n");
            System.exit(1);
        }

        return cost.toString();
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
            case WFI:
                cost.addDirMem(2);
                break;

            case POP:
            case LDMIA:
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
            case STRH:
                cost.addDirMem(1);
                cost.addMem(1);
                break;

            case LDR:
            case WFE:
                cost.addDirMem(1);
                cost.addMem(1);
                cost.addDir(1);
                break;

            case STR:
            case SEV:
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

            case FUNC_CALL:
            case FUNC_EXIT:
                // These are dummy instructions with no cost
                break;

            default:
                System.out.println("WCET: Unrecognized instruction");
                System.exit(1);
        }
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
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
