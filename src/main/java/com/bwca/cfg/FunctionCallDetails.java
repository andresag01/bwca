package com.bwca.cfg;

public class FunctionCallDetails
{
    private String calleeName;
    private long calleeAddr;
    private ISALine callInst;

    public FunctionCallDetails(String calleeName,
                               long calleeAddr,
                               ISALine callInst)
    {
        this.calleeName = calleeName;
        this.calleeAddr = calleeAddr;
        this.callInst = callInst;
    }

    public String getCalleeName()
    {
        return calleeName;
    }
}
