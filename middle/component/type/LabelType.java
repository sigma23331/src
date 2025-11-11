package middle.component.type;

/**
 * 标签类型 (LabelType)。
 * 这是 BasicBlock 的类型。
 */
public class LabelType implements Type {

    /**
     * 策略：使用单例模式，LabelType 全局唯一。
     */
    private static final LabelType instance = new LabelType();

    /**
     * 私有构造函数，防止外部 new。
     */
    private LabelType() {}

    /**
     * 获取 LabelType 的唯一实例。
     */
    public static LabelType getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        // // 提示：
        // // LLVM IR 中标签的类型是 "label"
        return "label";
    }
}
