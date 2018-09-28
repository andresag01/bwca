package com.bwca.cfg;

public enum Register
{
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

            case "R7":
                return R7;

            case "R8":
                return R8;

            case "R9":
                return R9;

            case "R10":
                return R10;

            case "R11":
                return R11;

            case "R12":
            case "IP":
                return R12;

            case "SP":
                return SP;

            case "LR":
                return LR;

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
