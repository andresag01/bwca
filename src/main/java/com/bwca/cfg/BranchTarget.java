package com.bwca.cfg;

public class BranchTarget
{
    // null means that we do not have this information
    private Long address;
    // null means that it is not a conditional instruction
    private Boolean cond;
    private ISABlock block;
    private int id;
    private boolean loopExit;

    public BranchTarget(Long address, Boolean cond)
    {
        this.address = address;
        this.cond = cond;
        this.block = null;
        this.id = 0;
        this.loopExit = false;
    }

    public BranchTarget(ISABlock block)
    {
        this.address = null;
        this.cond = null;
        this.block = block;
        this.id = 0;
        this.loopExit = false;
    }

    public String toString()
    {
        return address + " " + cond + " " + block;
    }

    public void setId(int id)
    {
        this.id = id;
    }

    public int getId()
    {
        return id;
    }

    public void setBlock(ISABlock block)
    {
        this.block = block;
    }

    public ISABlock getBlock()
    {
        return block;
    }

    public Boolean getCondition()
    {
        return cond;
    }

    public Long getAddress()
    {
        return address;
    }

    public boolean getLoopExit()
    {
        return loopExit;
    }

    public void setLoopExit(boolean loopExit)
    {
        this.loopExit = loopExit;
    }
}
