package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.Type;

/**
 * 截断 (trunc) 指令。
 * 例如：trunc i32 %val to i8
 */
public class TruncInst extends Instruction {

    /**
     * 构造函数。
     * @param name 结果名
     * @param valueToCast 要被转换的值 (例如 i32 %val)
     * @param targetType 目标类型 (例如 i8)
     */
    public TruncInst(String name, Value valueToCast, Type targetType) {
        // // 提示：
        super(targetType); // 1 个操作数
        this.reserveOperands(1);
        this.setName(name);
        this.setOperand(0, valueToCast);
    }

    public Value getValueToCast() { return this.getOperand(0); }

    @Override
    public boolean hasSideEffect() { return false; }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装
        // //    例如: "%c = trunc i32 %v to i8"
        Value val = this.getValueToCast();
        return this.getName() + " = trunc " +
               val.getType().toString() + " " + val.getName() +
               " to " + this.getType().toString();
    }

    @Override
    public Instruction copy() {
        TruncInst inst = new TruncInst(this.getName(),this.getValueToCast(),this.getType());
        inst.setName(this.getName() + "_copy");
        return inst;
    }
}
