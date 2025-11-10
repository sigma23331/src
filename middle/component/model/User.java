package middle.component.model;

import middle.component.type.Type;
import java.util.ArrayList;

/**
 * “使用者”类 (新版本)，继承自 Value。
 * 代表一个“使用”其他 Value 的“值”。(例如, 指令)
 */
public class User extends Value {

    /**
     * 策略：操作数列表。
     * 它存储的是 Use 对象，而不是像您源代码那样直接存 Value。
     */
    private final ArrayList<Use> operands;

    /**
     * 构造函数：指定类型和操作数的*数量*。
     */
    public User(Type type, int numOperands) {
        super(type);
        this.operands = new ArrayList<>(numOperands);
        // 提示：用 null 占位，并创建 Use 对象
        for (int i = 0; i < numOperands; i++) {
            // 在创建时，操作数的值为 null
            // Use 对象会自动在 null (如果非null)上注册
            Use use = new Use(this, null, i);
            this.operands.add(use);
        }
    }

    /**
     * 核心区别：设置指定索引的操作数。
     * (替换了您源代码中的 addOperand 和 modifyOperand)
     */
    public void setOperand(int index, Value value) {
        Use use = this.operands.get(index);
        // 2. (如果值没变，则无需操作)
        if (use.getValue() == value) {
            return;
        }
        use.clearOldValueUse();
        use.setNewValue(value);
    }

    /**
     * (内部方法) 由 Value.replaceAllUsesWith() 调用。
     */
    public void replaceOperandFromUse(Use use, Value newValue) {
        int index = use.getOperandIndex();
        setOperand(index,newValue);
    }

    /**
     * 获取指定索引的操作数 Value。
     */
    public Value getOperand(int index) {
        // 提示：从 Use 对象中获取真正的 Value
        return this.operands.get(index).getValue();
    }

    /**
     * 获取操作数数量。
     */
    public int getNumOperands() {
        return this.operands.size();
    }
}
