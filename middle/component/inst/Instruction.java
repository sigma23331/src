package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.User;
import middle.component.model.Value;
import middle.component.type.Type;

import java.util.Map;

/**
 * 所有指令的抽象基类。
 * 继承自 User，因为它“使用”操作数。
 */
public abstract class Instruction extends User {

    private BasicBlock parent;

    public Instruction(Type type) {
        super(type);
        this.parent = null;
    }

    public BasicBlock getParent() {
        return this.parent;
    }

    public void setParent(BasicBlock parent) {
        this.parent = parent;
    }

    public void removeOperands() {
        for (int i = 0; i < this.getNumOperands(); i++) {
            this.setOperand(i, null);
        }
    }

    public void remove() {
        if (this.parent != null) {
            this.removeOperands();
            this.parent.getInstructions().remove(this);
            this.parent = null;
        }
    }

    public abstract boolean hasSideEffect();

    @Override
    public abstract String toString();

    // ==========================================
    //  【新增】 克隆与重映射接口 (For Inlining)
    // ==========================================

    /**
     * 创建该指令的一个副本 (浅拷贝)。
     * <p>
     * 新指令的 type, name(通常重置), opcode 应该一致。
     * 新指令的 Operands 初始时应指向与原指令相同的旧 Value。
     * 之后会通过 {@link #remapOperands(Map)} 进行更新。
     *
     * @return 复制后的新指令 (尚未插入 Block)
     */
    public abstract Instruction copy();

    /**
     * 使用 valueMap 重映射指令的操作数。
     * <p>
     * 在内联过程中，我们需要将拷贝后的指令中的操作数（如果是函数内部定义的变量）
     * 替换为拷贝后的新变量。
     *
     * @param valueMap 映射表：旧 Value -> 新 Value
     */
    public void remapOperands(Map<Value, Value> valueMap) {
        // 遍历所有操作数
        for (int i = 0; i < getNumOperands(); i++) {
            Value oldVal = getOperand(i);
            // 如果映射表中存在该操作数的新版本（比如它是被内联函数内部的指令，或者参数）
            if (valueMap.containsKey(oldVal)) {
                // 将操作数替换为新值
                setOperand(i, valueMap.get(oldVal));
            }
        }
    }
}
