package middle.component.model;

import middle.component.type.PointerType;
import middle.component.type.Type;

/**
 * 全局变量 (例如 @g_var)。
 * * 关键区别：
 * 1. 继承自 Constant (不是 User)。
 * 2. 构造函数是干净的 (无副作用)。
 * 3. 初始值是一个 IR Constant (不是 InitialValue)。
 */
public class GlobalVar extends Constant {

    /**
     * 属性：全局变量的初始值。
     * 必须是 Constant (例如 ConstInt 或 ConstArray)。
     */
    private Constant initializer;

    /**
     * 属性：是否是常量 (例如 const int @g = 10)
     */
    private final boolean isConst;

    /**
     * 构造函数。
     * @param name IR 中的名字 (例如 @g_var)
     * @param type *必须* 是一个 PointerType (例如 i32* 或 [10 x i32]*)
     * @param initializer IR 初始值 (例如 ConstInt.get(0))
     * @param isConst 是否是 const
     */
    public GlobalVar(String name, PointerType type, Constant initializer, boolean isConst) {
        super(type); //类型必须是指针
        this.setName(name); //全局变量必须有名字
        this.initializer = initializer;
        this.isConst = isConst;
    }

    public Constant getInitializer() {
        return this.initializer;
    }

    public void setInitializer(Constant initializer) {
        //(这在某些优化中可能有用)
        this.initializer = initializer;
    }

    public boolean isConstant() {
        return this.isConst;
    }

    /**
     * 获取全局变量 *指向* 的类型 (指针解引用后的类型)。
     * 例如，如果 GlobalVar 的 Type 是 [10 x i32]*，
     * 那么 PointeeType 就是 [10 x i32]。
     */
    public Type getPointeeType() {
        // 提示：
        return ((PointerType)this.getType()).getPointeeType();
    }

    @Override
    public String toString() {
        // 1. 获取解引用后的类型 (getPointeeType())
        // 2. 获取初始值 (getInitializer())
        // 3. 构造 LLVM 字符串 (与您的 toString 逻辑类似)
        //    例如: "@g = dso_local global i32 0"
        //
        String linkage = "dso_local";
        String kind = this.isConst ? "constant" : "global";
        String typeStr = this.getPointeeType().toString();
        //
        // (处理 zeroinitializer 或 null)
        String initStr = "0"; //默认为 0
        if (this.initializer != null) {
            initStr = this.initializer.toString();
        }
        //
        return this.getName() + " = " + linkage + " " + kind + " " +
               typeStr + " " + initStr;
    }
}
