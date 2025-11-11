package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.VoidType; // (确保您有 VoidType)

/**
 * 内存写入指令 (store)。
 * store 指令没有返回值，所以它的 Type 是 void。
 */
public class StoreInst extends Instruction {

    /**
     * 构造函数。
     */
    public StoreInst(Value value, Value pointer) {
        super(VoidType.getInstance());
        this.reserveOperands(2);
        this.setOperand(0,value);
        this.setOperand(1,pointer);
    }

    public Value getValue() {
        return this.getOperand(0);
    }

    public Value getPointer() {
        return this.getOperand(1);
    }

    @Override
    public boolean hasSideEffect() {
        // Store 是 *有* 副作用的 (它修改内存状态)
        // * 关键区别：与您的源代码(false)不同
        return true;
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装
        // //    例如: "store i32 %v1, i32* %a_addr"

        Value val = this.getValue();
        Value ptr = this.getPointer();
        return "store " + val.getType().toString() + " " + val.getName() + ", " +
               ptr.getType().toString() + " " + ptr.getName();
    }
}
