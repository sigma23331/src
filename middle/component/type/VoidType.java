package middle.component.type;

public class VoidType implements Type {
    // 使用单例模式，因为Void类型全局只有一个
    private static final VoidType instance = new VoidType();

    private VoidType() {} // 私有构造函数

    public static VoidType getInstance() {
        return instance;
    }

    @Override
    public String toString() {
        return "void";
    }
}
