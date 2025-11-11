package middle.component.model;

/**
 * 辅助类：代表一个“使用”关系 (User -> Value)。
 * * 关键区别：现在存储它在 User 中的索引 (index)。
 */
public class Use {

    private final User user;
    private Value value;
    private final int index;

    /**
     * 构造函数：建立链接
     * @param user "使用者"
     * @param value "被使用者"
     * @param index 此 Use 在 User 的 operands 列表中的索引
     */
    public Use(User user, Value value, int index) {
        this.user = user;
        this.value = value;
        this.index = index;

        // 提示：
        // 1. 在被使用的 Value 上注册这个“Use”
        if (value != null) {
            value.addUse(this);
        }
    }

    public User getUser() { return this.user; }
    public Value getValue() { return this.value; }
    public int getIndex() { return this.index; }

    /**
     * 停止使用旧的 Value（由 User.setOperand 调用）
     */
    public void clearValueUse() {
        // 提示：
        if (this.value != null) {
            this.value.removeUse(this);
        }
    }

    /**
     * 设置新的 Value 并注册使用（由 User.setOperand 调用）
     */
    public void setNewValue(Value newValue) {
        // 提示：
        this.value = newValue;
        if (newValue != null) {
            newValue.addUse(this);
        }
    }
}
