package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class MemAsm extends MipsInstruction {
    private final AsmOp opCode;      // 操作码 (LW, SW)
    private final Register targetReg; // 目标/源寄存器 (rt)
    private final Register baseAddr;  // 基址寄存器 (base)
    private final int offsetVal;      // 偏移量 (offset)

    /**
     * 构造内存指令
     * 格式: op rt, offset(base)
     * 例如: lw $t0, -4($sp)
     *
     * @param op     操作码
     * @param reg    要加载/存储的寄存器
     * @param base   基址寄存器
     * @param offset 偏移量
     */
    public MemAsm(AsmOp op, Register reg, Register base, int offset) {
        super(); // 核心：自动加入 MipsFile
        this.opCode = op;
        this.targetReg = reg;
        this.baseAddr = base;
        this.offsetVal = offset;
    }

    public AsmOp getOpCode() {
        return opCode;
    }

    public Register getTargetReg() {
        return targetReg;
    }

    public Register getBaseAddr() {
        return baseAddr;
    }

    public int getOffsetVal() {
        return offsetVal;
    }

    @Override
    public String toString() {
        // 格式化为标准 MIPS 内存访问语法: op rt, offset(base)
        // 例如: sw $ra, 0($sp)
        return String.format("%s %s, %d(%s)",
                opCode.toString(), targetReg, offsetVal, baseAddr);
    }
}
