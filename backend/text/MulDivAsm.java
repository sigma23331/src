package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class MulDivAsm extends MipsInstruction {
    private final AsmOp opCode;
    private final Register rs; // 被乘数/被除数
    private final Register rt; // 乘数/除数

    /**
     * 构造乘除法指令
     * 对应 MipsBuilder: new MulDivAsm(reg1, AsmOp.DIV, reg2)
     * 注意：MIPS 语法通常是 "div rs, rt"
     */
    public MulDivAsm(Register rs, AsmOp op, Register rt) {
        super(); // 自动加入指令流
        this.rs = rs;
        this.opCode = op;
        this.rt = rt;
    }

    public AsmOp getOpCode() {
        return opCode;
    }

    public Register getRs() {
        return rs;
    }

    public Register getRt() {
        return rt;
    }

    @Override
    public String toString() {
        // 格式: div $t0, $t1
        return String.format("%s %s, %s",
                opCode.toString(), rs, rt);
    }
}