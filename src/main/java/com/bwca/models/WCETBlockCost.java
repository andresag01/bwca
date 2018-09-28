package com.bwca.models;

public class WCETBlockCost
{
    private int alu;
    private int mem;
    private int branch;
    private int dir;
    private int dirMem;

    public WCETBlockCost()
    {
        this.alu = 0;
        this.mem = 0;
        this.branch = 0;
        this.dir = 0;
        this.dirMem = 0;
    }

    public int getPositiveCost()
    {
        return alu + mem + branch + dir + dirMem;
    }

    public void addAlu(int cost)
    {
        this.alu += cost;
    }

    public void addMem(int cost)
    {
        this.mem += cost;
    }

    public void addBranch(int cost)
    {
        this.branch += cost;
    }

    public void addDir(int cost)
    {
        this.dir += cost;
    }

    public void addDirMem(int cost)
    {
        this.dirMem += cost;
    }
}
