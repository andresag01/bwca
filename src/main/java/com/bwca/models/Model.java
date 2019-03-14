package com.bwca.models;

import com.bwca.cfg.FunctionCallDetails;
import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.ISALine;

abstract public class Model
{
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
}
