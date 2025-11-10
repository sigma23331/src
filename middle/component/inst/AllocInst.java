package middle.component.inst;

import middle.component.type.PointerType;
import middle.component.type.Type;

/**
 * 内存分配指令 (alloc)。
 * 它在栈上分配一块内存，并返回一个指向该内存的 *指针*。
 * * 关键区别：没有 gepInsts 和 storeInsts 列表。
 */
public class AllocInst extends Instruction {

    /**
     * 属性：要分配的内存的类型 (例如 i32 或 [10 x i32])
     */
    private final Type allocatedType;

    /**
     * 构造函数。
     */
    public AllocInst(String name, Type allocatedType) {
        super(PointerType.get(allocatedType),0);
        this.allocatedType = allocatedType;
        this.setName(name);
    }

    public Type getAllocatedType() {
        return this.allocatedType;
    }

    @Override
    public boolean hasSideEffect() {
        // // 在 LLVM 中，alloca 本身没有副作用 (它只影响栈指针)
        // // * 关键区别：与您的源代码(true)不同
        return false;
    }

    @Override
    public String toString() {
        // 1. 拼装
        //    例如: "%a_addr = alloca i32"
        return this.getName() + " = alloca " + this.allocatedType.toString();
    }
}
