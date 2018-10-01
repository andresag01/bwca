package com.bwca.models;

import com.bwca.cfg.ISABlock;
import com.bwca.cfg.BranchTarget;
import com.bwca.cfg.ISALine;

abstract public class Model
{
    public abstract void addLineCost(ISABlock block, ISALine inst);

    public abstract void addEdgeCost(BranchTarget edge);

    public abstract String getBlockSummary(ISABlock block);

    public abstract String getEdgeSummary(BranchTarget edge);

    public abstract String getPositiveBlockCost(ISABlock block);

    public abstract String getNegativeEdgeCost(BranchTarget edge);

    public abstract String getInterceptCost();

    public abstract String getName();
}
