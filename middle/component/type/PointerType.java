package middle.component.type;

import java.util.HashMap;
import java.util.Map;

/**
 * 指针类型 (例如 i32*, [10 x i32]*)
 */
public class PointerType implements Type {

    /**
     * 属性：指针指向的类型 (Pointee Type)
     * (我们叫 pointeeType 而不是 targetType 来区分)
     */
    private final Type pointeeType;

    // --- 策略：缓存 (享元模式) ---
    // 确保所有指向同一类型的指针 (如所有 i32*) 都共享同一个实例
    private static final Map<Type, PointerType> cache = new HashMap<>();

    /**
     * 私有构造函数。
     */
    private PointerType(Type pointeeType) {
        this.pointeeType = pointeeType;
    }

    /**
     * 静态工厂方法，用于获取或创建指针类型实例。
     */
    public static PointerType get(Type pointeeType) {
        // 提示：使用 Map 的 computeIfAbsent 来实现缓存
        return cache.computeIfAbsent(pointeeType, PointerType::new);
    }

    // // 获取指针指向的类型
    public Type getPointeeType() {
        return this.pointeeType;
    }

    @Override
    public String toString() {
        // 提示：返回 pointeeType.toString() + "*"
        return this.pointeeType.toString() + "*";
    }
}