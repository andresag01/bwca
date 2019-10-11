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

import java.util.Map;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ISALine
{
    private long address;
    // Size (in bytes) of the instruction
    private long size;
    private String opcode;
    private String body;

    // Useful for branches
    private InstructionType type;
    // There will be one entry per destination of conditional branch
    // instructions
    private ArrayList<BranchTarget> branchTargets;
    private Predicate pred;

    private String targetFunction;
    private Long targetFunctionAddress;

    private Instruction inst;

    private LinkedList<String> infoMsgs;

    private boolean exit;

    private CFGConfiguration config;

    // Operands
    Register destReg;
    ArrayList<Register> regList;

    static final Pattern B_OPCODE = Pattern.compile(
        "^b"
        + "(?<predicate>eq|ne|cs|cc|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?$");
    static final Pattern REGLIST =
        Pattern.compile("^\\{(?<regList>(r[0-7]|pc|lr)"
                        + "(,\\s+(r[0-7]|pc|lr))*)\\}$");
    static final Pattern CPS_OPTS = Pattern.compile("^(?<opts>if?)$");
    static final Pattern SPLIT_REGLIST = Pattern.compile(",\\s+");
    static final Pattern REG_OPERANDS2_3 =
        Pattern.compile("^(?<dest>r[0-9]{1,2}|pc|lr|sp|ip|fp|sl|sb|wr),"
                        + "\\s+(?<src>.+)$");
    static final Pattern BRANCH_TARGET_ADDR =
        Pattern.compile("^(?<destAddr>[0-9a-f]+)\\s+<(?<funcName>[^>]+)>$");
    static final Pattern BRANCH_TARGET_ADDR_NO_FUNC =
        Pattern.compile("^[0-9a-f]+\\s+<[a-zA-Z0-9_]+\\+0x[0-9a-f]+>$");
    static final Pattern BRANCH_FUNC_NAME =
        Pattern.compile("^[a-zA-Z_][a-zA-Z_\\.0-9]*$");
    static final Pattern REGLIST_BASE =
        Pattern.compile("^(?<baseReg>r[0-7])!?,\\s+(?<regList>.*)$");

    public ISALine(long address,
                   String opcode,
                   String body,
                   CFGConfiguration config,
                   long funcBaseAddress,
                   long funcSize,
                   Map<String, SymbolTableRecord> symbolTable)
    {
        this.address = address;
        this.size = 0;
        this.opcode = opcode.trim();
        this.body = body.trim();
        this.branchTargets = new ArrayList<BranchTarget>();
        this.regList = new ArrayList<Register>();
        this.infoMsgs = new LinkedList<String>();
        this.targetFunction = null;
        this.targetFunctionAddress = null;
        this.exit = false;
        this.config = config;

        parseInstruction(funcBaseAddress, funcSize, symbolTable);
    }

    public ISALine(long address,
                   String opcode,
                   String body,
                   boolean exit,
                   String targetFunction,
                   long targetFunctionAddress,
                   Instruction inst,
                   InstructionType type)
    {
        this.address = address;
        this.size = 0;
        this.opcode = opcode.trim();
        this.body = body.trim();
        this.branchTargets = new ArrayList<BranchTarget>();
        this.regList = new ArrayList<Register>();
        this.infoMsgs = new LinkedList<String>();
        this.targetFunction = targetFunction;
        this.targetFunctionAddress = targetFunctionAddress;
        this.exit = exit;
        this.inst = inst;
        this.type = type;
    }

    public String toString()
    {
        return String.format("0x%08X: %s %s", address, opcode, body);
    }

    private void parse2And3RegisterOperands(String body)
    {
        Matcher match = REG_OPERANDS2_3.matcher(body);
        if (!match.matches())
        {
            System.out.println("Invalid register operands!");
            System.exit(1);
        }

        // Parse destination register
        destReg = Register.stringToRegister(match.group("dest"));
    }

    private void parseRegisterList(String body)
    {
        // Extract the register list
        Matcher match = REGLIST.matcher(body);
        if (!match.matches())
        {
            System.out.println("Invalid register list! " + body);
            System.exit(1);
        }

        for (String reg : SPLIT_REGLIST.split(match.group("regList")))
        {
            regList.add(Register.stringToRegister(reg));
        }
    }

    private void parseRegisterListWithBase(String body)
    {
        Matcher match = REGLIST_BASE.matcher(body);
        if (!match.matches())
        {
            System.out.println("Invalid register list base! " + body);
            System.exit(1);
        }
        parseRegisterList(match.group("regList").trim());
    }

    private void parseBranchTargetAddress(String body,
                                          long funcBaseAddress,
                                          long funcSize)
    {
        Matcher match = BRANCH_TARGET_ADDR.matcher(body);
        if (!match.matches())
        {
            System.out.println("Branch target address does not match!");
            System.exit(1);
        }

        long address = Long.parseLong(match.group("destAddr"), 16);
        String funcName = match.group("funcName");

        match = BRANCH_FUNC_NAME.matcher(funcName);
        if (match.matches())
        {
            targetFunction = funcName;
            targetFunctionAddress = address;
        }
        else if (type == InstructionType.BRANCH_LINK)
        {
            System.out.println("Could not parse function in '" + this + "'");
            System.exit(1);
        }

        if (inst == Instruction.B)
        {
            addBranchTargetIfFeasible(
                address, true, funcBaseAddress, funcSize);
            if (type == InstructionType.COND_BRANCH)
            {
                addBranchTargetIfFeasible(
                    this.address + 2, false, funcBaseAddress, funcSize);
            }
        }
    }

    private void addBranchTargetIfFeasible(long dest,
                                           boolean resolution,
                                           long funcBaseAddress,
                                           long funcSize)
    {
        Long unfeasibleDest = config.getUnfeasibleBranchDestination(address);

        if (!isAddressInFunction(funcBaseAddress, funcSize, dest))
        {
            // This is an exit branch which we do not support
            System.out.printf("Branch instruction at 0x%08x exits function "
                                  + "without return link\n",
                              address);
            System.exit(1);
        }
        else if (unfeasibleDest == null || unfeasibleDest != dest)
        {
            branchTargets.add(new BranchTarget(dest, resolution));
        }
    }

    public BranchTarget getBranchTarget(boolean cond)
    {
        for (BranchTarget target : branchTargets)
        {
            if (target.getCondition() == cond)
            {
                return target;
            }
        }

        return null;
    }

    public InstructionType getType()
    {
        return type;
    }

    public ArrayList<BranchTarget> getBranchTargets()
    {
        return branchTargets;
    }

    public long getAddress()
    {
        return address;
    }

    public Instruction getInstruction()
    {
        return inst;
    }

    public ArrayList<Register> getRegisterList()
    {
        return regList;
    }

    public String getTargetFunction()
    {
        return targetFunction;
    }

    public long getTargetFunctionAddress()
    {
        return targetFunctionAddress;
    }

    public LinkedList<String> getMissingInfoMessages()
    {
        return infoMsgs;
    }

    public boolean isExit()
    {
        return exit;
    }

    public String getOpcode()
    {
        return opcode;
    }

    private void processBranchLinkInstruction(String body,
                                              long funcBaseAddress,
                                              long funcSize)
    {
        Matcher match = BRANCH_TARGET_ADDR_NO_FUNC.matcher(body);

        if (match.matches())
        {
            // BL is being used in place of a regular B instruction
            inst = Instruction.B;
            type = InstructionType.BRANCH;
            pred = Predicate.AL;
        }
        else
        {
            // BL is actuall a function call
            inst = Instruction.BL;
            type = InstructionType.BRANCH_LINK;
        }

        parseBranchTargetAddress(body, funcBaseAddress, funcSize);
    }

    private void resolveUnconditionalBranch(long funcBaseAddress,
                                            long funcSize)
    {
        Long branchDestAddress = config.getBranchDestination(address);

        if (branchDestAddress == null)
        {
            String msg = String.format("branch 0x%08x <dest>", address);
            infoMsgs.add(msg);

            // Assume is an exit for simplicity
            exit = true;

            return;
        }

        boolean isInFunction =
            isAddressInFunction(funcBaseAddress, funcSize, branchDestAddress);
        if (isInFunction)
        {
            // This is a branch within the function
            branchTargets.add(new BranchTarget(branchDestAddress, true));
        }
        else
        {
            // This is an exit branch which we do not support
            System.out.printf("Branch instruction at 0x%08x exits function "
                                  + "without return link\n",
                              address);
            System.exit(1);
        }
    }

    private boolean isAddressInFunction(long funcBaseAddress,
                                        long funcSize,
                                        long address)
    {
        return (funcBaseAddress <= address &&
                address < funcBaseAddress + funcSize);
    }

    private void parseInstruction(long funcBaseAddress,
                                  long funcSize,
                                  Map<String, SymbolTableRecord> symbolTable)
    {
        pred = Predicate.AL;

        Matcher match = B_OPCODE.matcher(opcode);
        if (match.matches())
        {
            size = 2;
            inst = Instruction.B;
            String predStr = match.group("predicate");
            switch ((predStr == null) ? "al" : predStr)
            {
                case "eq":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.EQ;
                    break;

                case "ne":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.NE;
                    break;

                case "cs":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.CS;
                    break;

                case "cc":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.CC;
                    break;

                case "mi":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.MI;
                    break;

                case "pl":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.PL;
                    break;

                case "vs":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.VS;
                    break;

                case "vc":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.VC;
                    break;

                case "hi":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.HI;
                    break;

                case "ls":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.LS;
                    break;

                case "ge":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.GE;
                    break;

                case "lt":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.LT;
                    break;

                case "gt":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.GT;
                    break;

                case "le":
                    type = InstructionType.COND_BRANCH;
                    pred = Predicate.LE;
                    break;

                case "al":
                    type = InstructionType.BRANCH;
                    pred = Predicate.AL;
                    break;

                default:
                    System.out.println("Unrecognized predicate in branch");
                    System.exit(1);
            }
            parseBranchTargetAddress(body, funcBaseAddress, funcSize);
            return;
        }

        switch (opcode.toLowerCase())
        {
            case "sev":
                type = InstructionType.OTHER;
                inst = Instruction.SEV;
                size = 2;
                break;

            case "wfe":
                type = InstructionType.OTHER;
                inst = Instruction.WFE;
                size = 2;
                break;

            case "wfi":
                type = InstructionType.OTHER;
                inst = Instruction.WFI;
                size = 2;
                break;

            case "pop":
                parseRegisterList(body);
                inst = Instruction.POP;
                size = 2;
                type = InstructionType.OTHER;
                for (Register reg : regList)
                {
                    if (reg == Register.PC)
                    {
                        type = InstructionType.BRANCH;
                        // Assume this is a "return from function" instruction
                        exit = true;
                    }
                }
                break;

            case "ldmia":
                parseRegisterListWithBase(body);
                type = InstructionType.OTHER;
                inst = Instruction.LDMIA;
                size = 2;
                break;

            case "push":
                parseRegisterList(body);
                type = InstructionType.OTHER;
                inst = Instruction.PUSH;
                size = 2;
                break;

            case "stmia":
                parseRegisterListWithBase(body);
                type = InstructionType.OTHER;
                inst = Instruction.STMIA;
                size = 2;
                break;

            case "ldrb":
                type = InstructionType.OTHER;
                inst = Instruction.LDRB;
                size = 2;
                break;

            case "ldrh":
                type = InstructionType.OTHER;
                inst = Instruction.LDRH;
                size = 2;
                break;

            case "ldrsb":
                type = InstructionType.OTHER;
                inst = Instruction.LDRSB;
                size = 2;
                break;

            case "ldrsh":
                type = InstructionType.OTHER;
                inst = Instruction.LDRSH;
                size = 2;
                break;

            case "ldr":
                type = InstructionType.OTHER;
                inst = Instruction.LDR;
                size = 2;
                break;

            case "strb":
                type = InstructionType.OTHER;
                inst = Instruction.STRB;
                size = 2;
                break;

            case "strh":
                type = InstructionType.OTHER;
                inst = Instruction.STRH;
                size = 2;
                break;

            case "str":
                type = InstructionType.OTHER;
                inst = Instruction.STR;
                size = 2;
                break;

            case "blx":
                type = InstructionType.BRANCH_LINK;
                inst = Instruction.BLX;
                size = 2;
                // BLX instructions are not tagged with info about the call
                targetFunction = config.getFunctionCalleeName(address);
                if (targetFunction == null)
                {
                    String msg =
                        String.format("call 0x%08x <callee_name>", address);
                    infoMsgs.add(msg);
                }
                else
                {
                    // Use the symbol table to work out what address the branch
                    // is going to
                    SymbolTableRecord symbol = symbolTable.get(targetFunction);
                    if (symbol == null)
                    {
                        System.out.printf("Function %s in configuration file "
                                              + "does not exist in the symbol "
                                              + "table!\n",
                                          targetFunction);
                        System.exit(1);
                    }
                    else
                    {
                        targetFunctionAddress = symbol.getAddress();
                    }
                }
                break;

            case "bl":
                // Sometimes the bl instruction is used as a regular branch
                // within the function because the immediate of the regular
                // branches is not large enough to hold the immediate. We need
                // to identify this condition here and decide whether we are
                // dealing with a regular branch or a branch with link
                processBranchLinkInstruction(body, funcBaseAddress, funcSize);
                size = 4;
                break;

            case "bx":
                type = InstructionType.BRANCH;
                inst = Instruction.BX;
                size = 2;
                // Assume this is a "return from function" instruction
                exit = true;
                break;

            case "add":
            case "adds":
                inst = Instruction.ADD;
                size = 2;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    resolveUnconditionalBranch(funcBaseAddress, funcSize);
                }
                break;

            case "sub":
            case "subs":
                inst = Instruction.SUB;
                size = 2;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    resolveUnconditionalBranch(funcBaseAddress, funcSize);
                }
                break;

            case "lsl":
            case "lsls":
                type = InstructionType.OTHER;
                inst = Instruction.LSL;
                size = 2;
                break;

            case "cpy":
            case "cpys":
                inst = Instruction.CPY;
                size = 2;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    resolveUnconditionalBranch(funcBaseAddress, funcSize);
                }
                break;

            case "mov":
            case "movs":
                inst = Instruction.MOV;
                size = 2;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    resolveUnconditionalBranch(funcBaseAddress, funcSize);
                }
                break;

            case "orr":
            case "orrs":
                type = InstructionType.OTHER;
                inst = Instruction.ORR;
                size = 2;
                break;

            case "eor":
            case "eors":
                type = InstructionType.OTHER;
                inst = Instruction.EOR;
                size = 2;
                break;

            case "neg":
            case "negs":
                type = InstructionType.OTHER;
                inst = Instruction.NEG;
                size = 2;
                break;

            case "rev":
                type = InstructionType.OTHER;
                inst = Instruction.REV;
                size = 2;
                break;

            case "rev16":
                type = InstructionType.OTHER;
                inst = Instruction.REV16;
                size = 2;
                break;

            case "revsh":
                type = InstructionType.OTHER;
                inst = Instruction.REVSH;
                size = 2;
                break;

            case "mul":
            case "muls":
                type = InstructionType.OTHER;
                inst = Instruction.MUL;
                size = 2;
                break;

            case "ror":
            case "rors":
                type = InstructionType.OTHER;
                inst = Instruction.ROR;
                size = 2;
                break;

            case "sbc":
            case "sbcs":
                type = InstructionType.OTHER;
                inst = Instruction.SBC;
                size = 2;
                break;

            case "sxtb":
                type = InstructionType.OTHER;
                inst = Instruction.SXTB;
                size = 2;
                break;

            case "sxth":
                type = InstructionType.OTHER;
                inst = Instruction.SXTH;
                size = 2;
                break;

            case "nop":
                type = InstructionType.OTHER;
                inst = Instruction.NOP;
                size = 2;
                break;

            case "tst":
                type = InstructionType.OTHER;
                inst = Instruction.TST;
                size = 2;
                break;

            case "uxth":
                type = InstructionType.OTHER;
                inst = Instruction.UXTH;
                size = 2;
                break;

            case "uxtb":
                type = InstructionType.OTHER;
                inst = Instruction.UXTB;
                size = 2;
                break;

            case "mvn":
            case "mvns":
                type = InstructionType.OTHER;
                inst = Instruction.MVN;
                size = 2;
                break;

            case "lsr":
            case "lsrs":
                type = InstructionType.OTHER;
                inst = Instruction.LSR;
                size = 2;
                break;

            case "cmn":
                type = InstructionType.OTHER;
                inst = Instruction.CMN;
                size = 2;
                break;

            case "cmp":
                type = InstructionType.OTHER;
                inst = Instruction.CMP;
                size = 2;
                break;

            case "bic":
            case "bics":
                type = InstructionType.OTHER;
                inst = Instruction.BIC;
                size = 2;
                break;

            case "asr":
            case "asrs":
                type = InstructionType.OTHER;
                inst = Instruction.ASR;
                size = 2;
                break;

            case "and":
            case "ands":
                type = InstructionType.OTHER;
                inst = Instruction.AND;
                size = 2;
                break;

            case "adc":
            case "adcs":
                type = InstructionType.OTHER;
                inst = Instruction.ADC;
                size = 2;
                break;

            case "bkpt":
                type = InstructionType.OTHER;
                inst = Instruction.BKPT;
                // Assume this is a "halt" instruction for the simulator
                exit = true;
                size = 2;
                break;

            case "svc":
                type = InstructionType.OTHER;
                inst = Instruction.SVC;
                // Assume this is a "halt" instruction for the simulator
                exit = true;
                size = 2;
                break;

            case "cpsid":
                Matcher cpsOpts = CPS_OPTS.matcher(body);
                if (!cpsOpts.matches())
                {
                    System.out.printf("Unrecognized CPS options '%s'\n", body);
                    System.exit(1);
                }

                type = InstructionType.OTHER;
                if (cpsOpts.group("opts").equals("if"))
                {
                    inst = Instruction.CPS;
                }
                else if (cpsOpts.group("opts").equals("i"))
                {
                    inst = Instruction.CPSIF;
                }
                size = 2;
                break;

            case "udf":
                type = InstructionType.OTHER;
                inst = Instruction.UDF;
                size = 2;
                break;

            default:
                System.out.printf("Unrecognized instruction '%s' at 0x%08x\n",
                                  opcode,
                                  address);
                System.exit(1);
        }

        // Check if this is an exit block because the block is at the end of a
        // function and it is not a branch
        if (!isAddressInFunction(funcBaseAddress, funcSize, address + size))
        {
            exit = (type == InstructionType.OTHER ||
                    type == InstructionType.BRANCH_LINK) ?
                true :
                exit;
        }
    }
}
