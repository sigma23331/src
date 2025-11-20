package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class LiAsm extends MipsInstruction {
    // 目标寄存器
    private final Register target;
    // 要加载的立即数值
    private final int immValue;

    /**
     * 构造 Load Immediate 指令
     * 对应 MipsBuilder: new LiAsm(reg, 123)
     */
    public LiAsm(Register target, int immValue) {
        super(); // 自动加入指令流
        this.target = target;
        this.immValue = immValue;
    }

    public Register getTarget() {
        return target;
    }

    public int getImm() {
        return immValue;
    }

    @Override
    public String toString() {
        // 格式: li $t0, 100
        return String.format("%s %s, %d",
                AsmOp.LI.toString(), target, immValue);
    }
}