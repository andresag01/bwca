package com.bwca.models.ihgc.wca;

public class WCABlockCostIHGC
{
    private long alloc;
    private long funcCall;

    public WCABlockCostIHGC()
    {
        this.alloc = 0;
        this.funcCall = 0;
    }

    public void addAllocation(long size)
    {
        alloc += size;
    }

    public void addFunctionCall(long size)
    {
        funcCall += size;
    }

    public long getPositiveCost()
    {
        return alloc + funcCall;
    }

    public String toString()
    {
        return String.format(" *    - ALLOC: %d\n" +
                             " *    - FUNCS: %d\n",
                             alloc,
                             funcCall);
    }
}
