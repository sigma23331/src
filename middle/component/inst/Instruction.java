package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.User;
import middle.component.type.Type;

/**
 * 所有指令的抽象基类。
 * 继承自 User，因为它“使用”操作数。
 * * 关键区别：构造函数没有副作用 (不依赖 IRData)。
 */
public abstract class Instruction extends User {

    /**
     * 属性：这条指令所属的基本块。
     */
    private BasicBlock parent;

    /**
     * 构造函数。
     */
    public Instruction(Type type, int numOperands) {
        // 调用 User(Type type, int numOperands)
        super(type, numOperands);
        this.parent = null;
    }

    public BasicBlock getParent() {
        return this.parent;
    }

    /**
     * 由 BasicBlock.addInstruction() 调用，建立双向链接。
     */
    public void setParent(BasicBlock parent) {
        this.parent = parent;
    }

    /**
     * 指令是否具有副作用 (例如：修改内存、IO、跳转)。
     * 这对于死代码消除等优化至关重要。
     */
    public abstract boolean hasSideEffect();

    /**
     * 所有子类都必须实现一个有意义的 toString
     */
    @Override
    public abstract String toString();
}
