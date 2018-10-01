package com.bwca.models;

public class WCMABlockCost
{
    private double fetch;
    private double mem;

    public WCMABlockCost()
    {
        this.fetch = 0.0;
        this.mem = 0.0;
    }

    public void addFetch(double val)
    {
        fetch += val;
    }

    public void subFetch(double val)
    {
        fetch -= val;
    }

    public void addMem(double val)
    {
        mem += val;
    }

    public double getFetch()
    {
        return fetch;
    }

    public double getMem()
    {
        return mem;
    }
}
