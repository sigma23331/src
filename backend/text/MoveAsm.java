package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class MoveAsm extends MipsInstruction {
    private final Register toReg;   // 目标寄存器 (rd)
    private final Register fromReg; // 源寄存器 (rs)

    /**
     * 构造 Move 指令
     * 对应 MipsBuilder: new MoveAsm(rd, rs)
     * 汇编输出: move rd, rs
     */
    public MoveAsm(Register toReg, Register fromReg) {
        super(); // 自动加入指令流
        this.toReg = toReg;
        this.fromReg = fromReg;
    }

    public Register getToReg() {
        return toReg;
    }

    public Register getFromReg() {
        return fromReg;
    }

    @Override
    public String toString() {
        // 格式: move $t0, $t1
        return String.format("%s %s, %s",
                AsmOp.MOVE.toString(), toReg, fromReg);
    }
}