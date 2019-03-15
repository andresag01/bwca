package com.bwca.models.ihgc.wcgc;

import com.bwca.cfg.ISALine;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.InstructionType;
import com.bwca.cfg.Instruction;
import com.bwca.cfg.FunctionCallDetails;
import com.bwca.models.Model;
import com.bwca.models.ihgc.wcet.WCETModelIHGC;
import com.bwca.models.ihgc.wcma.WCMAModelIHGC;

public class WCGCModelIHGC extends Model
{
    private Model wcet;
    private Model wcma;

    public WCGCModelIHGC(int fetchWidthBytes)
    {
        wcet = new WCETModelIHGC();
        wcma = new WCMAModelIHGC(fetchWidthBytes);
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

        return String.format("%.2f", cost);
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

        return String.format("%.2f", cost);
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

    public void addFunctionCallDetailsCost(FunctionCallDetails call,
                                           String cost)
    {
        // Hack: The problem is that WCMA uses floats but WCET uses ints, so
        // when we use the previous solution to a function we might end up
        // giving floats to WCET and cause a failure. As a workaround, we
        // round up the function result to the next integer and use this
        // integer value for both WCET and WCMA. Asside from some precision
        // issues, this does not cause any inaccuracies because WCET and WCMA
        // end up being subtracted later on.
        cost = Integer.toString((int)Math.ceil(Double.parseDouble(cost)));

        wcet.addFunctionCallDetailsCost(call, cost);
        wcma.addFunctionCallDetailsCost(call, cost);
    }

    public String getObjectiveFunctionType()
    {
        return "min";
    }

    public String getFunctionCallCost(FunctionCallDetails call)
    {
        String wcmaCost = wcma.getFunctionCallCost(call);
        String wcetCost = wcma.getFunctionCallCost(call);

        double cost =
            Double.parseDouble(wcetCost) - Double.parseDouble(wcmaCost);

        if (cost < 0.0)
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
