package com.bwca.models;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;

public class WCMAModel extends Model
{
    private Map<ISABlock, WCMABlockCost> blocks;
    private Map<BranchTarget, WCMAEdgeCost> edges;

    private final long BYTES_PER_WORD = 4;

    public WCMAModel()
    {
        blocks = new HashMap<ISABlock, WCMABlockCost>();
        edges = new HashMap<BranchTarget, WCMAEdgeCost>();
    }

    public String getName()
    {
        return "wcma";
    }

    public String getBlockSummary(ISABlock block)
    {
        WCMABlockCost cost = blocks.get(block);
        return String.format("f=%.1f,m=%.1f", cost.getFetch(), cost.getMem());
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        WCMABlockCost cost = blocks.get(block);
        return String.format("%.1f", cost.getFetch() + cost.getMem());
    }

    public String getInterceptCost()
    {
        return null;
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        String cost = getNegativeEdgeCost(edge);
        return (cost == null) ? cost : "-" + cost;
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        WCMAEdgeCost cost = edges.get(edge);
        if (cost == null)
        {
            return null;
        }
        else
        {
            return String.format("%.1f", cost.getNegativeCost());
        }
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        WCMABlockCost cost = blocks.get(block);

        if (cost == null)
        {
            cost = new WCMABlockCost();
            blocks.put(block, cost);
        }

        boolean aligned = inst.getAddress() % BYTES_PER_WORD == 0;

        switch (inst.getInstruction())
        {
            case WFI:
                cost.addMem(2.0);
                break;

            case POP:
            case LDMIA:
                cost.addMem(2.0 + inst.getRegisterList().size());
                if (inst.getType() == InstructionType.BRANCH)
                {
                    // Cost of discarding already fetched instruction
                    cost.addFetch(1.0);
                    // Destination address is never known here
                    cost.addFetch(0.5);
                    // Fetching this instruction (and potentially discarding
                    // the following instruction in the fetch buffer
                    cost.addFetch(aligned ? 1.0 : 0.5);
                }
                else
                {
                    cost.addFetch(0.5);
                    // If this instruction is unaligned, fetch happens
                    // simultaneously with instruction execution
                    cost.subFetch(aligned ? 0.0 : 1.0);
                }
                break;

            case PUSH:
            case STMIA:
                cost.addMem(1.0 + inst.getRegisterList().size());
                cost.addFetch(0.5);
                // If this instruction is unaligned, fetch happens
                // simultaneously with instruction execution
                cost.subFetch(aligned ? 0.0 : 1.0);
                break;

            case LDR:
            case WFE:
                cost.addMem(3.0);
                cost.addFetch(0.5);
                // If this instruction is unaligned, fetch happens
                // simultaneously with instruction execution
                cost.subFetch(aligned ? 0.0 : 1.0);
                break;

            case LDRB:
            case LDRH:
            case LDRSB:
            case LDRSH:
            case STRB:
            case STRH:
            case STR:
            case SEV:
                cost.addMem(2.0);
                cost.addFetch(0.5);
                // If this instruction is unaligned, fetch happens
                // simultaneously with instruction execution
                cost.subFetch(aligned ? 0.0 : 1.0);
                break;

            case ADD:
            case SUB:
            case MOV:
            case CPY:
                if (inst.getType() == InstructionType.BRANCH)
                {
                    // Cost of discarding already fetched instruction
                    cost.addFetch(1.0);
                    // Destination address is never known here
                    cost.addFetch(0.5);
                    // Fetching this instruction (and potentially discarding
                    // the following instruction in the fetch buffer
                    cost.addFetch(aligned ? 1.0 : 0.5);
                }
                else
                {
                    // Cost of fetching this instruction
                    cost.addFetch(0.5);
                }
                break;

            case BL:
                // Cost of discarding already fetched instruction
                cost.addFetch(1.0);
                // Only add part of the fetch cost because if the instruction
                // is aligned then we would be double-counting
                long targetAddress = inst.getTargetFunctionAddress();
                cost.addFetch(
                    (targetAddress % BYTES_PER_WORD == 0) ?  0.0 : 0.5);
                // Fetching this instruction (and potentially discarding the
                // following instruction in the fetch buffer
                 cost.addFetch(aligned ? 1.0 : 1.5);
                break;

            case BLX:
            case BX:
                // Cost of discarding already fetched instruction
                cost.addFetch(1.0);
                // Destination address is never known here
                cost.addFetch(0.5);
                // Fetching this instruction (and potentially discarding
                // the following instruction in the fetch buffer
                cost.addFetch(aligned ? 1.0 : 0.5);
                // Cost of loading the new executable object metadata.
                cost.addMem(1.0);
                break;

            case B:
                // Cost of discarding already fetched instruction
                cost.addFetch(1.0);
                // Only add part of the fetch cost because if the instruction
                // is aligned then we would be double-counting
                BranchTarget target = inst.getBranchTarget(true);
                cost.addFetch(
                    (target.getAddress() % BYTES_PER_WORD == 0) ?  0.0 : 0.5);
                // Fetching this instruction (and potentially discarding
                // the following instruction in the fetch buffer
                cost.addFetch(aligned ? 1.0 : 0.5);
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
            case CPS:
            case SVC:
            case BKPT:
                cost.addFetch(0.5);
                break;

            default:
                System.out.println("WCMA: Unrecognized instruction " +
                                   inst.getInstruction().name());
                System.exit(1);
        }
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
    {
        if (edge.getCondition() == null || edge.getCondition())
        {
            return;
        }

        ISALine inst = block.getLastLine();
        boolean aligned = inst.getAddress() % BYTES_PER_WORD == 0;

        // Cost of discarding already fetched instruction
        double cost = 1.0;
        // Only add part of the fetch cost because if the instruction
        // is aligned then we would be double-counting
        cost += (edge.getAddress() % BYTES_PER_WORD == 0) ?  0.0 : 0.5;
        // Fetching this instruction (and potentially discarding
        // the following instruction in the fetch buffer
        cost += aligned ? 1.0 : 0.5;

        cost -= 0.5;
        WCMAEdgeCost edgeCost = edges.get(edge);

        if (edges.get(edge) == null)
        {
            edgeCost = new WCMAEdgeCost();
            edges.put(edge, edgeCost);
        }
        edgeCost.subFalseBranch(cost);
    }
}
