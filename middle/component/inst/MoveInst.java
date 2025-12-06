package middle.component.inst;

import middle.component.model.Value;

public class MoveInst extends Instruction {

    /**
     * 构造函数
     * 将 source 的值移动（复制）给 result
     * 通常用于 Phi 消除阶段生成的并行副本 (Parallel Copy)
     * @param result 目标值 (To) - 通常是一个变量或寄存器
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
        // 【关键修复】
        // 1. 获取 Value 对象
        Value to = getToValue();
        Value from = getFromValue();

        // 2. 使用 getName() 而不是 toString()
        //    toString() 会递归打印指令定义，不仅导致 IR 难看，还会在 Phi 不完整时导致 NPE
        // 3. 增加 null 检查，防止潜在的空指针异常
        String toStr = (to == null) ? "null" : to.getName();
        String fromStr = (from == null) ? "null" : from.getName();

        return "move " + toStr + ", " + fromStr;
    }
}