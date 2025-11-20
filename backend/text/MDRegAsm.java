package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class MDRegAsm extends MipsInstruction {
    private final AsmOp mdOp;    // MFHI, MFLO, MTHI, MTLO
    private final Register reg;  // 涉及的通用寄存器

    /**
     * 构造 HI/LO 寄存器传输指令
     * 对应 MipsBuilder: new MDRegAsm(AsmOp.MFLO, targetReg)
     */
    public MDRegAsm(AsmOp op, Register reg) {
        super(); // 自动加入指令流
        this.mdOp = op;
        this.reg = reg;
    }

    public AsmOp getMdOp() {
        return mdOp;
    }

    public Register getReg() {
        return reg;
    }

    @Override
    public String toString() {
        // 格式统一为: opcode register
        // 例如: mflo $t0  或者  mthi $t1
        return String.format("%s %s", mdOp.toString(), reg);
    }
}
