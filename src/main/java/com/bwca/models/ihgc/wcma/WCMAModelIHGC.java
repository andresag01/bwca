package com.bwca.models.ihgc.wcma;

import java.util.Map;
import java.util.HashMap;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;
import com.bwca.cfg.FunctionCallDetails;
import com.bwca.models.Model;

public class WCMAModelIHGC extends Model
{
    private Map<ISABlock, WCMABlockCostIHGC> blocks;
    private Map<BranchTarget, WCMAEdgeCostIHGC> edges;
    private Map<FunctionCallDetails, Double> calls;

    private int instsPerFetch;

    private static final int BYTES_PER_INST = 2;
    private static final int BYTES_PER_WORD = 4;

    public WCMAModelIHGC(int fetchWidthBytes)
    {
        blocks = new HashMap<ISABlock, WCMABlockCostIHGC>();
        edges = new HashMap<BranchTarget, WCMAEdgeCostIHGC>();
        calls = new HashMap<FunctionCallDetails, Double>();

        instsPerFetch = fetchWidthBytes / BYTES_PER_INST;

        if (fetchWidthBytes % 2 != 0)
        {
            System.out.println("Fetch width must be a power of 2\n");
            System.exit(1);
        }
    }

    public String getName()
    {
        return "wcma_ihgc";
    }

    public String getBlockSummary(ISABlock block)
    {
        WCMABlockCostIHGC cost = blocks.get(block);
        return String.format(
            "[f=%.2f,m=%.2f]", cost.getFetch(), cost.getMem());
    }

    public String getBlockDetails(ISABlock block)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format(" * %s%d:\n", "b", block.getId()));
        builder.append(blocks.get(block).toString());

        for (FunctionCallDetails call : block.getFunctionCallDependencies())
        {
            String callDetails = String.format(" *        - %s@0x%08x: %.2f\n",
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
        double cost = blocks.get(block).getPositiveCost();
        if (cost < 0.0)
        {
            return null;
        }
        return String.format("%.2f", cost);
    }

    public String getNegativeBlockCost(ISABlock block)
    {
        double cost = blocks.get(block).getPositiveCost();
        if (cost >= 0.0)
        {
            return null;
        }
        return String.format("%.2f", cost);
    }

    public String getPositiveEdgeCost(BranchTarget edge)
    {
        return null;
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        WCMAEdgeCostIHGC cost = edges.get(edge);
        if (cost == null)
        {
            return null;
        }
        else
        {
            return String.format("%.2f", cost.getNegativeCost());
        }
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addFunctionCallCost(ISABlock block, FunctionCallDetails call)
    {
        WCMABlockCostIHGC cost = blocks.get(block);
        Double callCost = calls.get(call);

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

        cost.addFunctionCall(callCost);
    }

    public void addFunctionCallDetailsCost(FunctionCallDetails call,
                                           String cost)
    {
        calls.put(call, Double.parseDouble(cost));
    }

    public String getObjectiveFunctionType()
    {
        return "max";
    }

    public String getFunctionCallCost(FunctionCallDetails call)
    {
        Double cost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Function call not registered with model!");
            System.exit(1);
        }

        return cost.toString();
    }

    private double costOfFetch(long instLen)
    {
        double single = 1.0 / instsPerFetch;
        return single * instLen;
    }

    private boolean isFetchingWhileMemoryAccess(long instAddress)
    {
        switch (instsPerFetch)
        {
            case 2:
                return instAddress % BYTES_PER_WORD != 0;

            default:
                // Check whether the address of the instruction is two
                // instructions away from the next boundary. In this case,
                // fetch happens simultaneously with instruction execution if
                // instAddress contains a memory access instruction
                long fetchWidthBytes = instsPerFetch * BYTES_PER_INST;
                long instOffset = instAddress % fetchWidthBytes;
                long offsetToNextFetch = 3 * BYTES_PER_INST;
                return instOffset + offsetToNextFetch == fetchWidthBytes;
        }
    }

    private double costOfBranchDiscard(long instAddress)
    {
        // Compute the cost of the fetching (and not executing) the remaining
        // instructions in the fetch buffer
        long fetchWidthBytes = instsPerFetch * BYTES_PER_INST;
        long instIndex = (instAddress % fetchWidthBytes) / BYTES_PER_INST;
        double discardedInstsInBuffer = 1.0 - costOfFetch(1) * instIndex;

        // Compute the cost of disregarding already placed fetch requests.
        // When the buffer only has space for 2 instructions this cost is
        // always present, but in all other cases the cost is incurred when the
        // branch is in any of the 3 slots at the back of the buffer
        double wastedMemReqCost = 0.0;
        switch (instsPerFetch)
        {
            case 2:
                wastedMemReqCost += 1.0;
                break;

            default:
                if (instIndex >= instsPerFetch - 3)
                {
                    wastedMemReqCost += 1.0;
                }
                break;
        }

        return discardedInstsInBuffer + wastedMemReqCost;
    }

    private double costOfFetchingBranchTarget(long targetAddress)
    {
        long fetchWidthBytes = instsPerFetch * BYTES_PER_INST;
        long instIndex = (targetAddress % fetchWidthBytes) / BYTES_PER_INST;
        return costOfFetch(1) * instIndex;
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        WCMABlockCostIHGC cost = blocks.get(block);

        if (cost == null)
        {
            cost = new WCMABlockCostIHGC();
            blocks.put(block, cost);
        }

        BranchTarget branchTarget;
        long targetAddress;
        long instAddress = inst.getAddress();
        double subFetch = 0.0;
        if (isFetchingWhileMemoryAccess(instAddress))
        {
            subFetch = 1.0;
        }

        switch (inst.getInstruction())
        {
            case WFI:
                cost.addMem(2.0);
                cost.addFetch(costOfFetch(1));
                // Subtract fetch cost if it happens during instruction exec
                cost.subFetch(subFetch);
                break;

            case POP:
            case LDMIA:
                cost.addMem(2.0 + inst.getRegisterList().size());
                if (inst.getType() == InstructionType.BRANCH)
                {
                    // Destination address is never known here
                    cost.addFetch(1.0 - costOfFetch(1));
                    // Fetching this instruction and (potentially) discarding
                    // the following instructions in the fetch buffer
                    cost.addFetch(costOfBranchDiscard(instAddress));
                }
                else
                {
                    cost.addFetch(costOfFetch(1));
                    // Subtract fetch cost if it happens during instruction
                    // exec
                    cost.subFetch(subFetch);
                }
                break;

            case PUSH:
            case STMIA:
                cost.addMem(1.0 + inst.getRegisterList().size());
                cost.addFetch(costOfFetch(1));
                // Subtract fetch cost if it happens during instruction exec
                cost.subFetch(subFetch);
                break;

            case LDR:
            case WFE:
                cost.addMem(3.0);
                cost.addFetch(costOfFetch(1));
                // Subtract fetch cost if it happens during instruction exec
                cost.subFetch(subFetch);
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
                cost.addFetch(costOfFetch(1));
                // Subtract fetch cost if it happens during instruction exec
                cost.subFetch(subFetch);
                break;

            case ADD:
            case SUB:
            case MOV:
            case CPY:
                if (inst.getType() == InstructionType.BRANCH)
                {
                    // Destination address is never known here
                    cost.addFetch(1.0 - costOfFetch(1));
                    // Fetching this instruction and (potentially) discarding
                    // the following instructions in the fetch buffer
                    cost.addFetch(costOfBranchDiscard(instAddress));
                }
                else
                {
                    // Cost of fetching this instruction
                    cost.addFetch(costOfFetch(1));
                }
                break;

            case BL:
                // Fetching this instruction and (potentially) discarding
                // the following instructions in the fetch buffer. Here the
                // instruction is twice the normal length so we have to add the
                // fetch cost yet again
                cost.addFetch(costOfFetch(1));
                cost.addFetch(
                    costOfBranchDiscard(instAddress + BYTES_PER_INST));
                // Only add part of the fetch cost depending on alignment
                targetAddress = inst.getTargetFunctionAddress();
                cost.addFetch(costOfFetchingBranchTarget(targetAddress));
                break;

            case BLX:
            case BX:
                // Destination address is never known here
                cost.addFetch(1.0 - costOfFetch(1));
                // Fetching this instruction and (potentially) discarding the
                // following instructions in the fetch buffer
                cost.addFetch(costOfBranchDiscard(instAddress));
                // Cost of loading the new executable object metadata.
                cost.addMem(1.0);
                break;

            case B:
                // Fetching this instruction and (potentially) discarding the
                // following instructions in the fetch buffer
                cost.addFetch(costOfBranchDiscard(instAddress));
                // Only add part of the fetch cost depending on alignment
                branchTarget = inst.getBranchTarget(true);
                if (branchTarget == null)
                {
                    // The branch will never be resolved to true, so this
                    // instruction never really branches and we do not need to
                    // add any more fetch costs
                    break;
                }
                targetAddress = branchTarget.getAddress();
                cost.addFetch(costOfFetchingBranchTarget(targetAddress));
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
                cost.addFetch(costOfFetch(1));
                break;

            case FUNC_EXIT:
                // These are dummy instructions with no cost
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

        // Subtract the cost of branching on a conditional B instruction
        ISALine inst = block.getLastLine();
        double cost = 0.0;

        if (inst.getBranchTarget(true) == null)
        {
            // The branch will never actually be executed, so no need to
            // subtract anything at the edge
            return;
        }

        // Fetching this instruction and (potentially) discarding the
        // following instructions in the fetch buffer
        long instAddress = inst.getAddress();
        cost += costOfBranchDiscard(instAddress);
        // Only add part of the fetch cost depending on alignment
        long targetAddress = inst.getBranchTarget(true).getAddress();
        cost += costOfFetchingBranchTarget(targetAddress);

        // Dont include the cost of fetching and executing this branch as a NOP
        cost -= costOfFetch(1);

        WCMAEdgeCostIHGC edgeCost = edges.get(edge);

        if (edges.get(edge) == null)
        {
            edgeCost = new WCMAEdgeCostIHGC();
            edges.put(edge, edgeCost);
        }
        edgeCost.subFalseBranch(cost);
    }
}
