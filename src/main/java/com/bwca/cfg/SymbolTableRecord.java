package com.bwca.cfg;

public class SymbolTableRecord
{
    private Long addr;
    private Long size;

    public SymbolTableRecord(long size)
    {
        this.addr = null;
        this.size = size;
    }

    public SymbolTableRecord(long addr, long size)
    {
        this.addr = addr;
        this.size = size;
    }

    public Long getAddress()
    {
        return addr;
    }

    public Long getSize()
    {
        return size;
    }
}
