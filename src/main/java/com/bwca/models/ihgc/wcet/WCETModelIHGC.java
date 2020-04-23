/**
 * MIT License
 *
 * Copyright (c) 2019 Andres Amaya Garcia, Kyriakos Georgiou
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.bwca.models.ihgc.wcet;

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
import com.bwca.models.Model;

public class WCETModelIHGC extends Model
{
    private Map<ISABlock, WCETBlockCostIHGC> blocks;
    private Map<BranchTarget, WCETEdgeCostIHGC> edges;
    private Map<FunctionCallDetails, Integer> calls;

    public WCETModelIHGC()
    {
        blocks = new HashMap<ISABlock, WCETBlockCostIHGC>();
        edges = new HashMap<BranchTarget, WCETEdgeCostIHGC>();
        calls = new HashMap<FunctionCallDetails, Integer>();
    }

    public void clear()
    {
        blocks = new HashMap<ISABlock, WCETBlockCostIHGC>();
        edges = new HashMap<BranchTarget, WCETEdgeCostIHGC>();
    }

    public String getName()
    {
        return "wcet_ihgc";
    }

    public String getBlockSummary(ISABlock block)
    {
        WCETBlockCostIHGC cost = blocks.get(block);
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

    public String getNegativeBlockCost(ISABlock block)
    {
        return null;
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        WCETEdgeCostIHGC cost = edges.get(edge);
        if (cost == null)
        {
            return null;
        }
        else
        {
            return String.format("%d", cost.getNegativeCost());
        }
    }

    public String getPositiveEdgeCost(BranchTarget edge)
    {
        return null;
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addFunctionCallCost(ISABlock block, FunctionCallDetails call)
    {
        WCETBlockCostIHGC cost = blocks.get(block);
        Integer callCost = calls.get(call);

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

        calls.put(call, (int)floor);
    }

    public void accumulateFunctionCallDetailsBlockCost(
        FunctionCallDetails call,
        ISABlock block,
        int repetitions)
    {
        Integer acc = calls.get(call);

        if (acc == null)
        {
            acc = 0;
        }

        acc += blocks.get(block).getPositiveCost() * repetitions;
        calls.put(call, acc);
    }

    public void accumulateFunctionCallDetailsEdgeCost(FunctionCallDetails call,
                                                      BranchTarget edge,
                                                      int repetitions)
    {
        Integer acc = calls.get(call);
        WCETEdgeCostIHGC edgeCost = edges.get(edge);

        if (acc == null)
        {
            acc = 0;
        }
        else if (edgeCost == null)
        {
            return;
        }

        acc -= repetitions * edgeCost.getNegativeCost();
        calls.put(call, acc);
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
            System.out.println("Function call not registered with model!");
            System.exit(1);
        }

        return cost.toString();
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        WCETBlockCostIHGC cost = blocks.get(block);

        if (cost == null)
        {
            cost = new WCETBlockCostIHGC();
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
                if (inst.getBranchTarget(true) != null)
                {
                    // Some edges can be eliminated via a config. So we need to
                    // be careful with this instruction because its full cost
                    // will not be consumed IFF the branch is never taken
                    cost.addAlu(2);
                    cost.addBranch(1);
                }
                else
                {
                    cost.addAlu(1);
                }
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
            case CPSF:
                /* Instruction repurposed for putchr() */
                cost.addAlu(1);
                break;

            case CPSIF:
                /* Instruction repurposed for __aeabi_uidivmod() */
                cost.addAlu(1);
                break;

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
        // This edge reduces the cost of the block if it is the false
        // resolution of a branch
        if (edge.getCondition() == null || edge.getCondition())
        {
            return;
        }

        ISALine inst = block.getLastLine();

        if (inst.getBranchTarget(true) == null)
        {
            // The branch will never actually be executed, so no need to
            // subtract anything at the edge
            return;
        }

        WCETEdgeCostIHGC cost = edges.get(edge);

        if (edges.get(edge) == null)
        {
            cost = new WCETEdgeCostIHGC();
            edges.put(edge, cost);
        }
        cost.subFalseBranch(2);
    }
}
