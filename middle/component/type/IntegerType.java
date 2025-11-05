package middle.component.type;

public class IntegerType implements Type {

    /**
     * 属性：位宽 (bits)
     */
    private final int bits;

    // --- 缓存常用实例 ---
    // (这才是单例模式在这里的正确用法，我们缓存已知的、固定位宽的类型)
    private static final IntegerType i1 = new IntegerType(1);
    private static final IntegerType i8 = new IntegerType(8);
    private static final IntegerType i32 = new IntegerType(32);

    /**
     * 私有构造函数，防止外部直接 new。
     */
    private IntegerType(int bits) {
        this.bits = bits;
    }

    /**
     * 策略：使用静态工厂方法获取实例。
     * 这是与原始代码(public static final)不同的地方，封装性更好。
     */
    public static IntegerType get(int bits) {
        // // 提示：根据位宽返回缓存的实例
        switch (bits) {
            case 1: return i1;
            case 8: return i8;
            case 32: return i32;
            default:
                // 或者根据需要支持其他位宽
                throw new IllegalArgumentException("Unsupported bit width: " + bits);
        }
    }

    // --- 我们可以提供便捷的 getters ---
    public static IntegerType get1() { return i1; }
    public static IntegerType get8() { return i8; }
    public static IntegerType get32() { return i32; }

    // // 提示：你可能需要一个 getter 来获取位宽
    public int getBitWidth() {
        return this.bits;
    }

    @Override
    public String toString() {
        // 提示：返回 "i" + bits
        return "i" + this.bits;
    }
}