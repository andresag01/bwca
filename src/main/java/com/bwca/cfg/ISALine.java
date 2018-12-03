package com.bwca.cfg;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class ISALine
{
    private long address;
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

    // Operands
    Register destReg;
    ArrayList<Register> regList;

    static final Pattern B_OPCODE = Pattern.compile(
        "^b"
        + "(?<predicate>eq|ne|cs|cc|mi|pl|vs|vc|hi|ls|ge|lt|gt|le|al)?$");
    static final Pattern REGLIST =
        Pattern.compile("^\\{(?<regList>(r[0-7]|pc|lr)"
                        + "(,\\s+(r[0-7]|pc|lr))*)\\}$");
    static final Pattern SPLIT_REGLIST = Pattern.compile(",\\s+");
    static final Pattern REG_OPERANDS2_3 =
        Pattern.compile("^(?<dest>r[0-9]{1,2}|pc|lr|sp|ip),\\s+(?<src>.+)$");
    static final Pattern BRANCH_TARGET_ADDR =
        Pattern.compile("^(?<destAddr>[0-9a-f]+)\\s+<(?<funcName>[^>]+)>$");
    static final Pattern BRANCH_FUNC_NAME = Pattern.compile("^[a-zA-Z_]\\w*$");
    static final Pattern REGLIST_BASE =
        Pattern.compile("^(?<baseReg>r[0-7])!?,\\s+(?<regList>.*)$");

    public ISALine(long address,
                   String opcode,
                   String body,
                   CFGConfiguration config)
    {
        this.address = address;
        this.opcode = opcode.trim();
        this.body = body.trim();
        this.branchTargets = new ArrayList<BranchTarget>();
        this.regList = new ArrayList<Register>();
        this.infoMsgs = new LinkedList<String>();
        this.targetFunction = null;
        this.targetFunctionAddress = null;
        this.exit = false;

        parseInstruction(config);
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

    private void parseBranchTargetAddress(String body)
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

        if (inst == Instruction.B)
        {
            branchTargets.add(new BranchTarget(address, true));
            if (type == InstructionType.COND_BRANCH)
            {
                branchTargets.add(
                    new BranchTarget(this.address + 2, false));
            }
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

    private void parseInstruction(CFGConfiguration config)
    {
        String infoMsgFmtBrandDst = "0x%08x %s (unknown branch destination)";
        pred = Predicate.AL;

        exit = config.isExit(address);

        Matcher match = B_OPCODE.matcher(opcode);
        if (match.matches())
        {
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
            parseBranchTargetAddress(body);
            return;
        }

        switch (opcode.toLowerCase())
        {
            case "sev":
                type = InstructionType.OTHER;
                inst = Instruction.SEV;
                break;

            case "wfe":
                type = InstructionType.OTHER;
                inst = Instruction.WFE;
                break;

            case "wfi":
                type = InstructionType.OTHER;
                inst = Instruction.WFI;
                break;

            case "pop":
                parseRegisterList(body);
                inst = Instruction.POP;
                type = InstructionType.OTHER;
                for (Register reg : regList)
                {
                    if (reg == Register.PC)
                    {
                        type = InstructionType.BRANCH;
                        if (!exit)
                        {
                            // This instruction is a "return from function"
                            // branch
                            infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                                       address,
                                                       "pop"));
                        }
                    }
                }
                break;

            case "ldmia":
                parseRegisterListWithBase(body);
                type = InstructionType.OTHER;
                inst = Instruction.LDMIA;
                break;

            case "push":
                parseRegisterList(body);
                type = InstructionType.OTHER;
                inst = Instruction.PUSH;
                break;

            case "stmia":
                parseRegisterListWithBase(body);
                type = InstructionType.OTHER;
                inst = Instruction.STMIA;
                break;

            case "ldrb":
                type = InstructionType.OTHER;
                inst = Instruction.LDRB;
                break;

            case "ldrh":
                type = InstructionType.OTHER;
                inst = Instruction.LDRH;
                break;

            case "ldrsb":
                type = InstructionType.OTHER;
                inst = Instruction.LDRSB;
                break;

            case "ldrsh":
                type = InstructionType.OTHER;
                inst = Instruction.LDRSH;
                break;

            case "ldr":
                type = InstructionType.OTHER;
                inst = Instruction.LDR;
                break;

            case "strb":
                type = InstructionType.OTHER;
                inst = Instruction.STRB;
                break;

            case "strh":
                type = InstructionType.OTHER;
                inst = Instruction.STRH;
                break;

            case "str":
                type = InstructionType.OTHER;
                inst = Instruction.STR;
                break;

            case "blx":
                type = InstructionType.BRANCH_LINK;
                inst = Instruction.BLX;
                // BLX instructions are not tagged with info about the call
                infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                           address,
                                           "blx"));
                break;

            case "bl":
                type = InstructionType.BRANCH_LINK;
                inst = Instruction.BL;
                parseBranchTargetAddress(body);
                break;

            case "bx":
                type = InstructionType.BRANCH;
                inst = Instruction.BX;
                if (!exit)
                {
                    // This instruction is a "return from function"
                    // branch
                    infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                               address,
                                               "bx"));
                }
                break;

            case "add":
            case "adds":
                inst = Instruction.ADD;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    if (!exit)
                    {
                        infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                                   address,
                                                   "bx"));
                    }
                }
                break;

            case "sub":
            case "subs":
                inst = Instruction.SUB;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    if (!exit)
                    {
                        infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                                   address,
                                                   "sub"));
                    }
                }
                break;

            case "lsl":
            case "lsls":
                type = InstructionType.OTHER;
                inst = Instruction.LSL;
                break;

            case "cpy":
            case "cpys":
                inst = Instruction.CPY;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    if (!exit)
                    {
                        infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                                   address,
                                                   "cpy"));
                    }
                }
                break;

            case "mov":
            case "movs":
                inst = Instruction.MOV;
                parse2And3RegisterOperands(body);
                type = InstructionType.OTHER;
                if (destReg == Register.PC)
                {
                    type = InstructionType.BRANCH;
                    if (!exit)
                    {
                        infoMsgs.add(String.format(infoMsgFmtBrandDst,
                                                   address,
                                                   "mov"));
                    }
                }
                break;

            case "orr":
            case "orrs":
                type = InstructionType.OTHER;
                inst = Instruction.ORR;
                break;

            case "eor":
            case "eors":
                type = InstructionType.OTHER;
                inst = Instruction.EOR;
                break;

            case "neg":
            case "negs":
                type = InstructionType.OTHER;
                inst = Instruction.NEG;
                break;

            case "rev":
                type = InstructionType.OTHER;
                inst = Instruction.REV;
                break;

            case "rev16":
                type = InstructionType.OTHER;
                inst = Instruction.REV16;
                break;

            case "revsh":
                type = InstructionType.OTHER;
                inst = Instruction.REVSH;
                break;

            case "mul":
            case "muls":
                type = InstructionType.OTHER;
                inst = Instruction.MUL;
                break;

            case "ror":
            case "rors":
                type = InstructionType.OTHER;
                inst = Instruction.ROR;
                break;

            case "sbc":
            case "sbcs":
                type = InstructionType.OTHER;
                inst = Instruction.SBC;
                break;

            case "sxtb":
                type = InstructionType.OTHER;
                inst = Instruction.SXTB;
                break;

            case "sxth":
                type = InstructionType.OTHER;
                inst = Instruction.SXTH;
                break;

            case "nop":
                type = InstructionType.OTHER;
                inst = Instruction.NOP;
                break;

            case "tst":
                type = InstructionType.OTHER;
                inst = Instruction.TST;
                break;

            case "uxth":
                type = InstructionType.OTHER;
                inst = Instruction.UXTH;
                break;

            case "uxtb":
                type = InstructionType.OTHER;
                inst = Instruction.UXTB;
                break;

            case "mvn":
            case "mvns":
                type = InstructionType.OTHER;
                inst = Instruction.MVN;
                break;

            case "lsr":
            case "lsrs":
                type = InstructionType.OTHER;
                inst = Instruction.LSR;
                break;

            case "cmn":
                type = InstructionType.OTHER;
                inst = Instruction.CMN;
                break;

            case "cmp":
                type = InstructionType.OTHER;
                inst = Instruction.CMP;
                break;

            case "bic":
            case "bics":
                type = InstructionType.OTHER;
                inst = Instruction.BIC;
                break;

            case "asr":
            case "asrs":
                type = InstructionType.OTHER;
                inst = Instruction.ASR;
                break;

            case "and":
            case "ands":
                type = InstructionType.OTHER;
                inst = Instruction.AND;
                break;

            case "adc":
            case "adcs":
                type = InstructionType.OTHER;
                inst = Instruction.ADC;
                break;

            case "bkpt":
                type = InstructionType.OTHER;
                inst = Instruction.BKPT;
                break;

            case "svc":
                type = InstructionType.OTHER;
                inst = Instruction.SVC;
                break;

            case "cps":
            case "cpsid":
                type = InstructionType.OTHER;
                inst = Instruction.CPS;
                break;

            default:
                System.out.println("Unrecognized instruction " + opcode);
                System.exit(1);
        }
    }
}
