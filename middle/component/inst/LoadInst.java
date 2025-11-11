package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.PointerType;
import middle.component.type.Type;

/**
 * 内存读取指令 (load)。
 */
public class LoadInst extends Instruction {

    /**
     * 构造函数。
     */
    public LoadInst(String name, Value pointer) { // pointer 必须是 PtrType
        super(((PointerType) pointer.getType()).getPointeeType());
        this.reserveOperands(1);
        Type pointeeType = ((PointerType) pointer.getType()).getPointeeType();
        this.setName(name);
        this.setOperand(0,pointer);
    }

    public Value getPointer() {
        return this.getOperand(0);
    }

    @Override
    public boolean hasSideEffect() {
        // 正常的 load 没有副作用
        // * 关键区别：与您的源代码(true)不同
        return false;
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装
        // //    例如: "%v1 = load i32, i32* %a_addr"
        Value ptr = this.getPointer();
        return this.getName() + " = load " + this.getType().toString() + ", " +
               ptr.getType().toString() + " " + ptr.getName();
    }
}