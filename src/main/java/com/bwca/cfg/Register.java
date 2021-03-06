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
package com.bwca.cfg;

public enum Register {
    R0(0),
    R1(1),
    R2(2),
    R3(3),
    R4(4),
    R5(5),
    R6(6),
    R7(7),
    R8(8),
    R9(9),
    R10(10),
    R11(11),
    R12(12),
    SP(13),
    LR(14),
    PC(15);

    private final int value;

    private Register(int value)
    {
        this.value = value;
    }

    public static Register stringToRegister(String reg)
    {
        switch (reg.toUpperCase())
        {
            case "R0":
                return R0;

            case "R1":
                return R1;

            case "R2":
                return R2;

            case "R3":
                return R3;

            case "R4":
                return R4;

            case "R5":
                return R5;

            case "R6":
                return R6;

            case "WR":
            case "R7":
                return R7;

            case "R8":
                return R8;

            case "SB":
            case "R9":
                return R9;

            case "SL":
            case "R10":
                return R10;

            case "FP":
            case "R11":
                return R11;

            case "R12":
            case "IP":
                return R12;

            case "SP":
            case "R13":
                return SP;

            case "R14":
            case "LR":
                return LR;

            case "R15":
            case "PC":
                return PC;

            default:
                System.out.println("Invalid register " + reg);
                System.exit(1);
        }

        return PC;
    }

    public int getIndex()
    {
        return value;
    }
}
