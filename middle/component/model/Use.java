package middle.component.model;

/**
 * 辅助类：代表一个“使用”关系 (User -> Value)。
 * 它连接了“使用者”(User) 和“被使用者”(Value)。
 * * 关键设计：这个类的构造函数 *主动* 在两端注册自己。
 */
public class Use {

    /**
     * 属性：谁在“使用” (例如：一条 add 指令)。
     */
    private final User user;

    /**
     * 属性：被“使用”的“值” (例如：add 指令的第一个操作数 %v1)。
     */
    private Value value;

    /**
     * 属性：这个操作数在 User 的操作数列表中的索引。
     * (这是一个与源代码和上个提示都不同的新设计)
     */
    private final int operandIndex;

    /**
     * 构造函数：建立 User 和 Value 之间的双向链接。
     */
    public Use(User user, Value value, int operandIndex) {
        this.user = user;
        this.value = value;
        this.operandIndex = operandIndex;

        // // 提示：关键步骤！
        // // 在被使用的 Value 上注册这个“Use”
        if (value != null) {
            value.addUse(this);
        }
    }

    /**
     * 获取使用者。
     */
    public User getUser() {
        return this.user;
    }

    /**
     * 获取被使用的值。
     */
    public Value getValue() {
        return this.value;
    }

    /**
     * 获取此操作数在 User 中的索引。
     */
    public int getOperandIndex() {
        return this.operandIndex;
    }

    /**
     * (内部方法) 停止使用旧的 Value。
     * 由 User.setOperand() 调用。
     */
    public void clearOldValueUse() {
        // 提示：
        if (this.value != null) {
            this.value.removeUse(this);
        }
    }

    /**
     * (内部方法) 设置新的 Value。
     * 由 User.setOperand() 调用。
     */
    public void setNewValue(Value newValue) {
        this.value = newValue;
        if (this.value != null) {
            this.value.addUse(this);
        }
    }
}
