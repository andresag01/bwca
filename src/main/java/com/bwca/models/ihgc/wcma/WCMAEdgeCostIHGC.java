package com.bwca.models.ihgc.wcma;

public class WCMAEdgeCostIHGC
{
    private double falseBranch;

    public WCMAEdgeCostIHGC()
    {
        this.falseBranch = 0.0;
    }

    public double getNegativeCost()
    {
        return falseBranch;
    }

    public void subFalseBranch(double cost)
    {
        falseBranch += cost;
    }
}
