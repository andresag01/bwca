package com.bwca.cfg;

public class FunctionCallDetails
{
    private String calleeName;
    private long calleeAddr;
    private ISALine callerInst;

    public FunctionCallDetails(String calleeName,
                               long calleeAddr,
                               ISALine callerInst)
    {
        this.calleeName = calleeName;
        this.calleeAddr = calleeAddr;
        this.callerInst = callerInst;
    }

    public String getCalleeName()
    {
        return calleeName;
    }

    public long getCallAddress()
    {
        return (callerInst == null) ? 0 : callerInst.getAddress();
    }
}
