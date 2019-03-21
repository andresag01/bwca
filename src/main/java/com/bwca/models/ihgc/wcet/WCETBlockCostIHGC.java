/**
 * MIT License
 *
 * Copyright (c) 2019 Andres Amaya Garcia, Kyriakos Georgiou
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.bwca.models.ihgc.wcet;

public class WCETBlockCostIHGC
{
    private int alu;
    private int mem;
    private int branch;
    private int dir;
    private int dirMem;
    private int funcCall;

    public WCETBlockCostIHGC()
    {
        this.alu = 0;
        this.mem = 0;
        this.branch = 0;
        this.dir = 0;
        this.dirMem = 0;
        this.funcCall = 0;
    }

    public String toString()
    {
        return String.format(" *    - ALU: %d\n"
                                 + " *    - MEM: %d\n"
                                 + " *    - BRANCH: %d\n"
                                 + " *    - DIR: %d\n"
                                 + " *    - DIRMEM: %d\n"
                                 + " *    - FUNCS: %d\n",
                             alu,
                             mem,
                             branch,
                             dir,
                             dirMem,
                             funcCall);
    }

    public int getPositiveCost()
    {
        return alu + mem + branch + dir + dirMem + funcCall;
    }

    public void addFunctionCall(int cost)
    {
        this.funcCall += cost;
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
