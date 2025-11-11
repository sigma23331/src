package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.Type;

/**
 * 零扩展 (zext) 指令。
 * 例如：zext i1 %cmp to i32
 */
public class ZextInst extends Instruction {

    /**
     * 构造函数。
     * @param name 结果名
     * @param valueToCast 要被转换的值 (例如 i1 %cmp)
     * @param targetType 目标类型 (例如 i32)
     */
    public ZextInst(String name, Value valueToCast, Type targetType) {
        // 提示：
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
        // 1. 拼装
        //    例如: "%v = zext i1 %cmp to i32"
        Value val = this.getValueToCast();
        return this.getName() + " = zext " +
                val.getType().toString() + " " + val.getName() +
                " to " + this.getType().toString();
    }
}
