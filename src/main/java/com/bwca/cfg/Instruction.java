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

public enum Instruction {
    SEV,
    WFE,
    WFI,
    POP,
    LDMIA,
    PUSH,
    STMIA,
    LDRB,
    LDRH,
    LDRSB,
    LDRSH,
    LDR,
    STRB,
    STRH,
    STR,
    BLX,
    BL,
    BX,
    B,
    ADD,
    SUB,
    LSL,
    CPY,
    MOV,
    ORR,
    EOR,
    NEG,
    REV,
    REV16,
    REVSH,
    MUL,
    ROR,
    SBC,
    SXTB,
    SXTH,
    NOP,
    TST,
    UXTH,
    UXTB,
    MVN,
    LSR,
    CMN,
    CMP,
    BIC,
    ASR,
    AND,
    ADC,
    BKPT,
    SVC,
    CPS,
    /* Undefined instruction. Probably emitted for padding */
    UDF,
    FUNC_EXIT,
}
