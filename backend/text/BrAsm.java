package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class BrAsm extends MipsInstruction {
    private final String targetLabel; // 跳转目标标签
    private final Register lhs;       // 左操作数 (rs)
    private final Register rhs;       // 右操作数 (rt, 可能为空)
    private final int immVal;         // 立即数 (当 rt 为空时使用)
    private final AsmOp conditionOp;  // 比较操作符

    /**
     * 寄存器比较跳转构造函数
     * 例如: beq $t0, $t1, label
     */
    public BrAsm(String targetLabel, Register lhs, AsmOp op, Register rhs) {
        super();
        this.targetLabel = targetLabel;
        this.lhs = lhs;
        this.conditionOp = op;
        this.rhs = rhs;
        this.immVal = 0;
    }

    /**
     * 立即数比较跳转构造函数
     * 例如: beq $t0, 100, label (虽然标准MIPS少见，但这适配你的编译器中间层)
     */
    public BrAsm(String targetLabel, Register lhs, AsmOp op, int immVal) {
        super();
        this.targetLabel = targetLabel;
        this.lhs = lhs;
        this.conditionOp = op;
        this.rhs = null; // 标记右操作数寄存器不存在
        this.immVal = immVal;
    }

    public String getTargetLabel() {
        return targetLabel;
    }

    public AsmOp getOp() {
        return conditionOp;
    }

    public Register getRs() {
        return lhs;
    }

    public Register getRt() {
        return rhs;
    }

    public int getImm() {
        return immVal;
    }

    @Override
    public String toString() {
        // 逻辑变更：使用三元运算符手动判断，替代 Objects.requireNonNullElse
        String secondOperand = (rhs != null) ? rhs.toString() : String.valueOf(immVal);

        // 格式: op rs, rt/imm, label
        return String.format("%s %s, %s, %s",
                conditionOp.toString(), lhs, secondOperand, targetLabel);
    }
}