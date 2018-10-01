package com.bwca.models;

public class WCMAEdgeCost
{
    private double falseBranch;

    public WCMAEdgeCost()
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
