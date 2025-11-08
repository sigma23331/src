package middle.component.model;

import middle.component.type.IntegerType;
import java.util.HashMap;
import java.util.Map;

/**
 * 整数常量 (例如: 10, 0, -1)。
 * 继承自 Constant。
 */
public class ConstInt extends Constant {

    /**
     * 属性：这个常量实际的 int 值。
     */
    private final int value;

    // --- 策略：缓存 ---
    // 确保所有相同的整数（如所有 `i32 10`）都共享同一个实例
    // 结构: Map<Type, Map<Value, ConstInt>>
    private static final Map<IntegerType, Map<Integer, ConstInt>> cache = new HashMap<>();

    /**
     * 私有构造函数，强制使用 .get()
     */
    private ConstInt(IntegerType type, int value) {
        super(type);
        this.value = value;
        // 提示：将常量的“名字”设置为它的值，方便 toString
        this.setName(String.valueOf(value));
    }

    /**
     * 静态工厂方法：获取 ConstInt 实例。
     */
    public static ConstInt get(IntegerType type, int value) {
        // 1. 根据类型处理值 (与您的 setName 逻辑相同)
        int effectiveValue = value;
        if (type == IntegerType.get8()) { //检查单例
            effectiveValue = value & 0xFF;
        }
        // 2. 查找或创建第一层 Map (基于 elementType)
        Map<Integer,ConstInt> innerMap = cache.computeIfAbsent(
                type,
                k->new HashMap<>()
        );
        // // 3. 查找或创建并返回实例
        return innerMap.computeIfAbsent(
            effectiveValue,
            k -> new ConstInt(type, k) // k 是 effectiveValue
        );
    }

    /**
     * 获取这个常量的 int 值。
     */
    public int getValue() {
        return this.value;
    }

    /**
     * 对于常量，我们重写 toString()，使其只返回名字（即值）。
     * 这在 IR 生成时（如 `add i32 %1, 10`）更方便。
     */
    @Override
    public String toString() {
        // 提示：
        // 1. 直接返回 getName()
        return this.getName();
    }
}
