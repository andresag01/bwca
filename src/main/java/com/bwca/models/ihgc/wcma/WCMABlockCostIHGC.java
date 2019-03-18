package com.bwca.models.ihgc.wcma;

public class WCMABlockCostIHGC
{
    private double addFetch;
    private double subFetch;
    private double mem;
    private double funcCall;

    public WCMABlockCostIHGC()
    {
        this.addFetch = 0.0;
        this.subFetch = 0.0;
        this.mem = 0.0;
        this.funcCall = 0.0;
    }

    public void addFunctionCall(double val)
    {
        funcCall += val;
    }

    public void addFetch(double val)
    {
        addFetch += val;
    }

    public void subFetch(double val)
    {
        subFetch += val;
    }

    public void addMem(double val)
    {
        mem += val;
    }

    public double getAddFetch()
    {
        return addFetch;
    }

    public double getSubFetch()
    {
        return subFetch;
    }

    public double getMem()
    {
        return mem;
    }

    public double getPositiveCost()
    {
        return addFetch - subFetch + mem + funcCall;
    }

    public String toString()
    {
        return String.format(" *    - MEM: %.2f\n"
                                 + " *    - FETCH+: %.2f\n"
                                 + " *    - FETCH-: %.2f\n"
                                 + " *    - FUNCS: %.2f\n",
                             mem,
                             addFetch,
                             subFetch,
                             funcCall);
    }
}
