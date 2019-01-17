package com.bwca.cfg;

public class LoopBound
{
    private long lbound;
    private long ubound;

    public LoopBound(long lbound, long ubound)
    {
		this.lbound = lbound;
		this.ubound = ubound;
    }

    public long getUpperBound()
    {
        return ubound;
    }

    public long getLowerBound()
    {
        return lbound;
    }
}
