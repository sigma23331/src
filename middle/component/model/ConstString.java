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
     * 属性：原始 Java 字符串 (例如 "Hello\n")
     */
    private final String rawString;

    /**
     * 属性：处理后的 LLVM 字符串 (例如 "Hello\0A\00")
     */
    private final String llvmString;

    /**
     * 属性：处理后的字符串的字节长度 (包括 \00)
     */
    private final int byteLength;

    /**
     * 构造函数 (由 Module 的 getOrAddConstString 调用)
     * @param name IR 中的名字 (例如 @.str.0)
     * @param rawString 原始 Java 字符串 (例如 "Hello\n")
     */
    public ConstString(String name, String rawString) {
        // // 提示：
        // // 1. (与您的代码相同) 处理换行符
        // //    (一个更完整的实现会处理所有转义)
        // String processed = rawString.replace("\n", "\\0A");
        // this.llvmString = processed + "\\00"; // 添加 null 终止符
        // this.rawString = rawString;
        //
        // // 2. (与您的代码不同) 计算 *正确* 的字节长度
        // //    例如 "Hello\n" -> "Hello\0A" (6 字节) + \00 (1 字节) = 7 字节
        // this.byteLength = rawString.length() + 1;
        //
        // // 3. *关键区别*：
        // //    Type 是 [N x i8]，而不是 [N x i8]*
        // super(ArrayType.get(IntegerType.get8(), this.byteLength));
        //
        // // 4. 设置名字
        // this.setName(name);
    }

    public String getRawString() {
        return this.rawString;
    }

    @Override
    public String toString() {
        // // 提示：生成完整的 LLVM 定义
        // // 例如: "@.str.0 = private unnamed_addr constant [7 x i8] c"Hello\0A\00""
        // // (this.getType() 现在是正确的 ArrayType)
        // return this.getName() + " = private unnamed_addr constant " +
        //        this.getType().toString() + " c\"" + this.llvmString + "\"";
    }
}
