package com.bwca.models.ihgc.wcma;

public class WCMABlockCostIHGC
{
    private double fetch;
    private double mem;
    private double funcCall;

    public WCMABlockCostIHGC()
    {
        this.fetch = 0.0;
        this.mem = 0.0;
        this.funcCall = 0.0;
    }

    public void addFunctionCall(double val)
    {
        funcCall += val;
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

    public double getPositiveCost()
    {
        return fetch + mem + funcCall;
    }

    public String toString()
    {
        return String.format(" *    - MEM: %.2f\n"
                                 + " *    - FETCH: %.2f\n"
                                 + " *    - FUNCS: %.2f\n",
                             mem,
                             fetch,
                             funcCall);
    }
}
