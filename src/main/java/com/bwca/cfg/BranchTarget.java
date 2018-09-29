package com.bwca.cfg;

public class BranchTarget
{
    // null means that we do not have this information
    private Long address;
    // null means that it is not a conditional instruction
    private Boolean cond;
    private ISABlock block;
    private int id;

    public BranchTarget(Long address, Boolean cond)
    {
        this.address = address;
        this.cond = cond;
        this.block = null;
        this.id = 0;
    }

    public BranchTarget(ISABlock block)
    {
        this.address = null;
        this.cond = null;
        this.block = block;
        this.id = 0;
    }

    public String toString()
    {
        return address + " " + cond + " " + block;
    }

    public BranchTarget copy()
    {
        BranchTarget cpy =
            new BranchTarget(this.address, this.cond);
        cpy.block = this.block;
        cpy.id = this.id;

        return cpy;
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
}
