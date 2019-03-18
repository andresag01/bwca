package com.bwca.models.ihgc.wcgc;

import java.util.List;
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
import com.bwca.models.ihgc.wcet.WCETModelIHGC;
import com.bwca.models.ihgc.wcma.WCMAModelIHGC;

public class WCGCModelIHGC extends Model
{
    private Model wcet;
    private Model wcma;
    Map<FunctionCallDetails, Double> calls;

    public WCGCModelIHGC(int fetchWidthBytes)
    {
        wcet = new WCETModelIHGC();
        wcma = new WCMAModelIHGC(fetchWidthBytes);

        calls = new HashMap<FunctionCallDetails, Double>();
    }

    public void clear()
    {
        wcet.clear();
        wcma.clear();
    }

    public String getName()
    {
        return "wcgc_ihgc";
    }

    public String getBlockSummary(ISABlock block)
    {
        String wcmaSummary = wcma.getBlockSummary(block);
        String wcetSummary = wcet.getBlockSummary(block);
        return String.format(
            "[wcet=[%s],wcma=[%s]]", wcetSummary, wcmaSummary);
    }

    public String getBlockDetails(ISABlock block)
    {
        StringBuilder builder = new StringBuilder();

        builder.append(String.format(" * %s%d:\n", "b", block.getId()));
        builder.append(" *    WCMA\n");
        builder.append(wcma.getBlockDetails(block));
        builder.append(" *    WCET\n");
        builder.append(wcet.getBlockDetails(block));

        return builder.toString();
    }

    public String getEdgeSummary(BranchTarget edge)
    {
        String wcmaSummary = wcma.getEdgeSummary(edge);
        String wcetSummary = wcet.getEdgeSummary(edge);
        return String.format("wcet=[%s],wcma=[%s]",
                             (wcetSummary == null) ? "" : wcetSummary,
                             (wcmaSummary == null) ? "" : wcmaSummary);
    }

    public String getPositiveBlockCost(ISABlock block)
    {
        String wcmaCost = wcma.getPositiveBlockCost(block);
        String wcetCost = wcet.getPositiveBlockCost(block);

        double cost =
            Double.parseDouble(wcetCost) - Double.parseDouble(wcmaCost);

        if (cost < 0.0)
        {
            return null;
        }

        return String.format("%.2f", cost);
    }

    public String getNegativeBlockCost(ISABlock block)
    {
        String wcmaCost = wcma.getPositiveBlockCost(block);
        String wcetCost = wcet.getPositiveBlockCost(block);

        double cost =
            Double.parseDouble(wcetCost) - Double.parseDouble(wcmaCost);

        if (cost >= 0.0)
        {
            return null;
        }

        return String.format("%.2f", Math.abs(cost));
    }

    public String getNegativeEdgeCost(BranchTarget edge)
    {
        String wcmaCost = wcma.getNegativeEdgeCost(edge);
        String wcetCost = wcet.getNegativeEdgeCost(edge);

        if (wcmaCost == null && wcetCost == null)
        {
            return null;
        }
        else if (wcmaCost == null && wcetCost != null ||
                 wcmaCost != null && wcetCost == null)
        {
            System.out.println("WCGC: WCET and WCMA edge are not null "
                               + "simultaneously");
            System.exit(1);
        }

        double cost =
            Double.parseDouble(wcmaCost) - Double.parseDouble(wcetCost);
        if (cost > 0.0)
        {
            return null;
        }

        return String.format("%.2f", Math.abs(cost));
    }

    public String getPositiveEdgeCost(BranchTarget edge)
    {
        String wcmaCost = wcma.getNegativeEdgeCost(edge);
        String wcetCost = wcet.getNegativeEdgeCost(edge);

        if (wcmaCost == null && wcetCost == null)
        {
            return null;
        }
        else if (wcmaCost == null && wcetCost != null ||
                 wcmaCost != null && wcetCost == null)
        {
            System.out.println("WCGC: WCET and WCMA edge are not null "
                               + "simultaneously");
            System.exit(1);
        }

        double cost =
            Double.parseDouble(wcmaCost) - Double.parseDouble(wcetCost);
        if (cost <= 0.0)
        {
            return null;
        }

        return String.format("%.2f", cost);
    }

    public String getInterceptCost()
    {
        return null;
    }

    public void addFunctionCallCost(ISABlock block, FunctionCallDetails call)
    {
        wcet.addFunctionCallCost(block, call);
        wcma.addFunctionCallCost(block, call);
    }

    private void resolveFunctionCallCost(List<ISABlock> blocks,
                                         FunctionCallDetails call,
                                         CFGSolution solution,
                                         Model model)
    {
        int blockSolution, edgeSolution;

        for (ISABlock block : blocks)
        {
            blockSolution = solution.getBlockSolution(block.getId());
            model.accumulateFunctionCallDetailsBlockCost(
                call, block, blockSolution);

            for (BranchTarget edge : block.getEdges())
            {
                edgeSolution = solution.getEdgeSolution(edge.getId());
                model.accumulateFunctionCallDetailsEdgeCost(
                    call, edge, edgeSolution);
            }
        }
    }

    public void addFunctionCallDetailsCost(ISAFunction caller,
                                           FunctionCallDetails call,
                                           CFGSolution solution)
    {
        // Compute the cost of the function for each of the models
        resolveFunctionCallCost(caller.getBlocks(), call, solution, wcet);
        resolveFunctionCallCost(caller.getBlocks(), call, solution, wcma);

        // Add the WCGC cost of this call
        calls.put(call,
                  Double.parseDouble(solution.getObjectiveFunctionSolution()));
    }

    public String getObjectiveFunctionType()
    {
        return "min";
    }

    public String getFunctionCallCost(FunctionCallDetails call)
    {
        Double cost = calls.get(call);

        if (cost == null)
        {
            System.out.println("Function call not registered in model!\n");
            System.exit(1);
        }
        else if (cost < 0.0)
        {
            System.out.println("WCGC function call cost < 0.0");
            System.exit(1);
        }

        return Double.toString(cost);
    }

    public void addLineCost(ISABlock block, ISALine inst)
    {
        wcet.addLineCost(block, inst);
        wcma.addLineCost(block, inst);
    }

    public void addEdgeCost(ISABlock block, BranchTarget edge)
    {
        wcet.addEdgeCost(block, edge);
        wcma.addEdgeCost(block, edge);
    }
}
