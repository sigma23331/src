package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.ArrayType;
import middle.component.type.PointerType;
import middle.component.type.Type;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * GetElementPtr (GEP) 指令。
 * 用于计算数组或结构体中元素的地址。
 * 它 *不* 访问内存（不 load/store），它只做指针运算。
 */
public class GepInst extends Instruction {

    /**
     * 构造函数。
     * GEP 总是返回一个指针。
     * @param name 结果名
     * @param pointer 基础指针 (例如 i32* %a 或 [10 x i32]* %arr)
     * @param indexes 索引列表 (例如 0, %i)
     */
    public GepInst(String name, Value pointer, ArrayList<Value> indexes) {
        // 提示：
        // 1. GEP 有 N+1 个操作数 (pointer + N 个 index)
        super(calculateResultType(pointer, indexes));
        this.reserveOperands(indexes.size() + 1);
        this.setName(name);
        //
        // 2. 设置操作数
        this.setOperand(0, pointer);
        for (int i = 0; i < indexes.size(); i++) {
            this.setOperand(i + 1, indexes.get(i));
        }
    }

    /**
     * GEP 的核心逻辑：计算返回的指针类型
     */
    private static Type calculateResultType(Value pointer, ArrayList<Value> indexes) {

        Type baseType = pointer.getType();
        Type currentType;

        if (baseType instanceof PointerType) {
            // --- 情况 1：基础是 alloca (例如 [10 x i32]*) ---
            currentType = ((PointerType) baseType).getPointeeType();
        } else {
            // --- 情况 2：基础是全局常量 (例如 [13 x i8]) ---
            currentType = baseType;
        }

        // (注意：GEP 的第一个索引是针对 PointeeType/BaseType 的)
        // (我们从 i = 1 开始循环，因为 GEP 的第一个索引(i=0)不改变类型)
        for (int i = 1; i < indexes.size(); i++) {
            // 每次索引，我们就深入一层
            if (currentType instanceof ArrayType) {
                currentType = ((ArrayType) currentType).getElementType();
            } else {
                // (您不支持结构体，所以这里不需要 else)
            }
        }

        // 最终返回的是指向那个深层类型的指针
        return PointerType.get(currentType);
    }

    public Value getPointer() { return this.getOperand(0); }

    @Override
    public boolean hasSideEffect() { return false; } //GEP 只是计算，无副作用

    @Override
    public String toString() {
        // 1. 拼装索引字符串
        String indexStr = "";
        for (int i = 1; i < getNumOperands(); i++) {
            Value idx = this.getOperand(i);
            indexStr += ", " + idx.getType().toString() + " " + idx.getName();
        }

        Value ptr = this.getPointer();    // (例如 @.str.0)
        Type baseType = ptr.getType();  // (例如 [13 x i8])

        Type pointeeType; // 这是 "inbounds" 关键字后面的类型
        Type pointerType; // 这是 GEP 接收的指针类型

        // *** 这是第二个修正的逻辑 ***
        if (baseType instanceof PointerType) {
            // --- 情况 1：基础是 alloca (例如 %arr.addr) ---
            // baseType 是 [10 x i32]*
            pointeeType = ((PointerType) baseType).getPointeeType(); // [10 x i32]
            pointerType = baseType; // [10 x i32]*
        } else {
            // --- 情况 2：基础是全局常量 (例如 @.str.0) ---
            // baseType 是 [13 x i8]
            pointeeType = baseType; // [13 x i8]
            // *关键修正*：指针类型是 baseType 的指针！
            pointerType = PointerType.get(baseType); // [13 x i8]*
        }
        // **************************

        // 2. 拼装 GEP 字符串
        //    (现在 pointerType 将是 [13 x i8]*)

        return this.getName() + " = getelementptr inbounds " +
                pointeeType.toString() + ", " +
                pointerType.toString() + " " +  // <-- 已修正
                ptr.getName() +
                indexStr;
    }
}
