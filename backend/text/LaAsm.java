package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;

public class LaAsm extends MipsInstruction {
    // 目的寄存器
    private final Register dstReg;
    // 目标标签名 (通常是全局变量名或字符串标签)
    private final String labelName;

    /**
     * 构造 Load Address 指令
     * 对应 MipsBuilder: new LaAsm(reg, "label_name")
     */
    public LaAsm(Register dstReg, String labelName) {
        super(); // 自动加入指令流
        this.dstReg = dstReg;
        this.labelName = labelName;
    }

    public Register getDstReg() {
        return dstReg;
    }

    public String getLabelName() {
        return labelName;
    }

    @Override
    public String toString() {
        // 使用 AsmOp 枚举的 toString (我们之前改写过，会输出小写 "la")
        // 格式: la $t0, var_name
        return String.format("%s %s, %s",
                AsmOp.LA.toString(), dstReg, labelName);
    }
}
