package com.bwca.models.ihgc.wcet;

public class WCETEdgeCostIHGC
{
    private int falseBranch;

    public WCETEdgeCostIHGC()
    {
        this.falseBranch = 0;
    }

    public int getNegativeCost()
    {
        return falseBranch;
    }

    public void subFalseBranch(int cost)
    {
        falseBranch += cost;
    }
}
