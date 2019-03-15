package com.bwca.models;

import com.bwca.cfg.FunctionCallDetails;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.ISALine;
import com.bwca.models.ihgc.wcet.WCETModelIHGC;
import com.bwca.models.ihgc.wca.WCAModelIHGC;
import com.bwca.models.ihgc.wcma.WCMAModelIHGC;
import com.bwca.models.ihgc.wcgc.WCGCModelIHGC;

abstract public class Model
{
    private static final String[][] MODELS = {
        { "wcet_ihgc", "Worst-Case Execution Time for the IHGC processor" },
        { "wca_ihgc", "Worst-Case Allocation for the IHGC processor" },
        { "wcma_ihgc",
          "Worst-Case Memory Access cycles for the IHGC processor" },
        { "wcgc_ihgc",
          "Worst-Case Garbage Collection cycles for the IHGC processor" },
    };

    public abstract void addLineCost(ISABlock block, ISALine inst);

    public abstract void addEdgeCost(ISABlock block, BranchTarget edge);

    public abstract void addFunctionCallCost(ISABlock block,
                                             FunctionCallDetails call);

    public abstract void addFunctionCallDetailsCost(FunctionCallDetails call,
                                                    String cost);

    public abstract String getBlockSummary(ISABlock block);

    public abstract String getBlockDetails(ISABlock block);

    public abstract String getEdgeSummary(BranchTarget edge);

    public abstract String getPositiveBlockCost(ISABlock block);

    public abstract String getNegativeEdgeCost(BranchTarget edge);

    public abstract String getInterceptCost();

    public abstract String getName();

    public abstract String getObjectiveFunctionType();

    public abstract String getFunctionCallCost(FunctionCallDetails call);

    public static void printModelsList()
    {
        StringBuilder builder = new StringBuilder();
        for (String[] model : MODELS)
        {
            builder.append(String.format("    %6s  %s\n", model[0], model[1]));
        }
        System.out.println("Available models:");
        System.out.print(builder.toString());
    }

    public static Model createModel(String modelOption, int fetchWidthBytes)
    {
        switch (modelOption)
        {
            case "wcet_ihgc":
                return new WCETModelIHGC();

            case "wca_ihgc":
                return new WCAModelIHGC();

            case "wcma_ihgc":
                return new WCMAModelIHGC(fetchWidthBytes);

            case "wcgc_ihgc":
                return new WCGCModelIHGC(fetchWidthBytes);

            default:
                return null;
        }
    }
}
