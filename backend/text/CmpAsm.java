package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class CmpAsm extends MipsInstruction {
    private final Register dest; // rd
    private final AsmOp opCode;  // op
    private final Register left; // rs
    private final Register right;// rt

    /**
     * 比较指令构造函数
     * 例如: seq $t0, $t1, $t2 (如果 t1 == t2 则 t0 = 1)
     */
    public CmpAsm(Register rd, AsmOp op, Register rs, Register rt) {
        super(); // 自动加入 MipsFile
        this.dest = rd;
        this.opCode = op;
        this.left = rs;
        this.right = rt;
    }

    public Register getLeftReg() {
        return left;
    }

    public Register getRightReg() {
        return right;
    }

    public Register getDestReg() {
        return dest;
    }

    @Override
    public String toString() {
        // 原始代码: return op.name().toLowerCase() + " " + rd + ", " + rs + ", " + rt;
        // 修改为 StringBuilder 拼接，逻辑相同但写法不同
        StringBuilder sb = new StringBuilder();
        sb.append(opCode.toString()) // AsmOp 中我们已经重写了 toString 为小写
                .append(" ")
                .append(dest)
                .append(", ")
                .append(left)
                .append(", ")
                .append(right);
        return sb.toString();
    }
}
