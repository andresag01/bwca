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
