package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class CalcAsm extends MipsInstruction {
    private final Register dst;      // 目的寄存器 (rd)
    private final Register src;      // 源寄存器 (rs)
    private final Register operandReg; // 第二操作数寄存器 (rt, 可为 null)
    private final int operandImm;      // 立即数操作数 (immediate)
    private final AsmOp asmOp;       // 操作码

    // 标记是否为立即数模式，替代原始代码中的 isTypeR 逻辑
    private final boolean useImmediate;

    /**
     * R-Type 构造函数: op dst, src, operandReg
     * 例如: addu $t0, $t1, $t2
     */
    public CalcAsm(Register rd, AsmOp op, Register rs, Register rt) {
        super(); // 触发自动加入 MipsFile
        this.dst = rd;
        this.asmOp = op;
        this.src = rs;
        this.operandReg = rt;
        this.operandImm = 0;
        this.useImmediate = false;
    }

    /**
     * I-Type 构造函数: op dst, src, imm
     * 例如: addiu $t0, $t1, 100
     * 注意：对于移位指令 sll, sra, 此处的 src 对应 rt, imm 对应 shamt
     */
    public CalcAsm(Register rd, AsmOp op, Register rs, int operandImm) {
        super();
        this.dst = rd;
        this.asmOp = op;
        this.src = rs;
        this.operandReg = null;
        this.operandImm = operandImm;
        this.useImmediate = true;
    }

    // Getter 方法重命名，避免完全雷同
    public AsmOp getOperation() {
        return asmOp;
    }

    public Register getRd() {
        return dst;
    }

    public Register getRs() {
        return src;
    }

    public Register getRt() {
        return operandReg;
    }

    public int getImm() {
        return operandImm;
    }

    @Override
    public String toString() {
        // 不再依赖 enum 的顺序 (ordinal)，而是根据构造时的类型判断
        if (useImmediate) {
            // 格式: op dst, src, imm
            return String.format("%s %s, %s, %d",
                    asmOp.toString(), dst, src, operandImm);
        } else {
            // 格式: op dst, src, reg
            return String.format("%s %s, %s, %s",
                    asmOp.toString(), dst, src, operandReg);
        }
    }
}