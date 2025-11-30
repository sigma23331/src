package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.User;
import middle.component.type.Type;

/**
 * 所有指令的抽象基类。
 * 继承自 User，因为它“使用”操作数。
 */
public abstract class Instruction extends User {

    /**
     * 属性：这条指令所属的基本块。
     */
    private BasicBlock parent;

    /**
     * 构造函数。
     */
    public Instruction(Type type) {
        // 调用 User(Type type) - 新版构造函数不需要 numOperands
        super(type);
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
     * (新) 移除该指令的所有操作数。
     * 用于在删除指令前，断开 Use-Def 链，防止后续分析出错。
     */
    public void removeOperands() {
        // 由于 User 中的 operands 列表是 private 的，我们无法直接 clear()。
        // 但是，我们可以利用 setOperand 接口将所有操作数置为 null。

        // 逻辑：
        // setOperand(i, null) 会内部调用 use.clearValueUse()，
        // 从而将当前 User 从对应 Value 的 useList 中移除。
        for (int i = 0; i < this.getNumOperands(); i++) {
            this.setOperand(i, null);
        }

        // 虽然 operands 列表的大小没有变（变成了全是 null 的列表），
        // 但图的连接关系（Def-Use Chain）已经完全断开，满足 Mem2Reg 删除指令的需求。
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
