package middle.component.inst;

import middle.component.model.Value;

public class MoveInst extends Instruction {

    /**
     * 构造函数
     * 将 source 的值移动（复制）给 result
     * 通常用于 Phi 消除阶段生成的并行副本 (Parallel Copy)
     * * @param result 目标值 (To) - 通常是一个变量或寄存器
     * @param source 源值 (From) - 被复制的值
     */
    public MoveInst(Value result, Value source) {
        // Move 指令的类型通常跟随目标值的类型
        super(result.getType());

        // 提示：
        // 1. 我们约定第 0 个操作数是目标 (Destination)
        this.addOperand(result);

        // 2. 我们约定第 1 个操作数是源 (Source)
        this.addOperand(source);
    }

    /**
     * 获取目标值 (Destination)
     */
    public Value getToValue() {
        // 提示：对应 addOperand 的顺序
        return this.getOperand(0);
    }

    /**
     * 获取源值 (Source)
     */
    public Value getFromValue() {
        // 提示：对应 addOperand 的顺序
        return this.getOperand(1);
    }

    @Override
    public boolean hasSideEffect() {
        return true; // Move 指令修改了目标变量的状态，视为有副作用
    }

    @Override
    public String toString() {
        // 提示：
        // 拼装 Move 指令，格式通常为 "move dst, src"
        // 例如: "move %t0, 10"
        return "move " + getToValue().toString() + ", " + getFromValue().toString();
    }
}