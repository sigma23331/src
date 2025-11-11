package middle.component.model;

import middle.component.type.Type;
import java.util.ArrayList;

/**
 * “使用者”类 (重构后)。
 * * 关键区别：
 * 1. 构造函数不再接收 numOperands。
 * 2. 添加了 addOperand() 用于动态添加 (Phi, Call)。
 * 3. 添加了 reserveOperands() + setOperand() 用于固定添加 (Binary, Store)。
 */
public class User extends Value {

    /**
     * 属性：操作数列表 (存储 Use 对象)。
     * 现在是一个可动态增长的列表。
     */
    private final ArrayList<Use> operands;

    /**
     * 构造函数 (不再需要 numOperands)。
     */
    public User(Type type) {
        super(type);
        this.operands = new ArrayList<>();
    }

    /**
     * (新) 辅助方法：为固定大小的指令预留插槽。
     * (BinaryInst, StoreInst 等应首先调用此方法)
     */
    protected void reserveOperands(int numOperands) {
        // 提示：
        // 1. 确保列表容量
        this.operands.ensureCapacity(numOperands);
        
        // 2. 用 null 占位，创建 Use 对象
        for (int i = 0; i < numOperands; i++) {
            //(Use 构造函数会自动链接 null)
            Use use = new Use(this, null, i);
            this.operands.add(use);
        }
    }

    /**
     * (新) 动态添加一个操作数。
     * (PhiInst 和 CallInst 将使用此方法)
     */
    public void addOperand(Value value) {
        // 提示：
        // 1. 确定新索引
        int index = this.operands.size();
        
        // 2. 创建新的 Use 对象（它会自动链接）
        Use newUse = new Use(this, value, index);
        
        // 3. 添加到列表
        this.operands.add(newUse);
    }

    /**
     * (重构) 设置指定索引的操作数。
     */
    public void setOperand(int index, Value value) {
        // 提示：
        // 1. 获取该索引对应的 Use 对象
        Use use = this.operands.get(index);
        
        // 2. (如果值没变，则无需操作)
        if (use.getValue() == value) {
            return;
        }
        
        // 3. 告诉 Use 对象，它将不再使用旧值
        use.clearValueUse();
        
        // 4. 告诉 Use 对象，它将开始使用新值
        use.setNewValue(value);
    }

    public Value getOperand(int index) {
        return this.operands.get(index).getValue();
    }

    public int getNumOperands() {
        return this.operands.size();
    }

    public void replaceOperandFromUse(Use use, Value newValue) {
        int index = use.getIndex();
        this.setOperand(index, newValue);
    }
}
