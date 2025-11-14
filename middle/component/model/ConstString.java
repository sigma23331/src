package middle.component.model;

import middle.component.type.ArrayType;
import middle.component.type.IntegerType;
// (您可能需要一个工具类来处理转义)
// import middle.util.StringUtil;

/**
 * 字符串常量定义 (例如 @.str = ...)。
 * * 关键区别：
 * 1. 继承自 Constant。
 * 2. Type 是 ArrayType (例如 [7 x i8])，*不是* PointerType。
 * 3. 构造函数是干净的 (没有副作用)。
 */
public class ConstString extends Constant {

    /**
     * 属性：*已处理* 的 LLVM 字符串 (例如 "Hello\0A\00")
     */
    private final String llvmString;

    /**
     * 属性：*已计算* 的字节长度 (例如 7)
     */
    private final int byteLength;

    /**
     * (新) 构造函数 (由 Module 调用)
     * @param name IR 中的名字 (例如 @.str.0)
     * @param llvmString *已处理* 的 LLVM 字符串 (例如 "Hello\0A\00")
     * @param byteLength *已计算* 的字节长度 (例如 7)
     */
    public ConstString(String name, String llvmString, int byteLength) {

        // --- 1. *** `super()` 在第一行被调用 *** ---
        //    (我们直接使用 Module 传来的已计算好的 byteLength)
        super(ArrayType.get(IntegerType.get8(), byteLength));

        // --- 2. super() 已完成，现在设置本类的字段 ---
        this.llvmString = llvmString;
        this.byteLength = byteLength;
        // (我们不再需要存储 rawString 了)

        // 3. 设置名字
        this.setName(name);
    }

    @Override
    public String toString() {
        // (此方法保持不变，它现在可以完美工作)
        return this.getName() + " = private unnamed_addr constant " +
                this.getType().toString() + " c\"" + this.llvmString + "\"";
    }
}