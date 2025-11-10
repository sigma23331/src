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
        // // 提示：
        // // 1. GEP 有 N+1 个操作数 (pointer + N 个 index)
        // super(calculateResultType(pointer, indexes), indexes.size() + 1);
        // this.setName(name);
        //
        // // 2. 设置操作数
        // this.setOperand(0, pointer);
        // for (int i = 0; i < indexes.size(); i++) {
        //     this.setOperand(i + 1, indexes.get(i));
        // }
    }

    /**
     * GEP 的核心逻辑：计算返回的指针类型
     */
    private static Type calculateResultType(Value pointer, ArrayList<Value> indexes) {
        // // 提示：
        // // GEP 返回的永远是 PtrType
        // // 它的 PointeeType 是由索引“深入”计算得出的
        //
        // Type currentType = ((PointerType) pointer.getType()).getPointeeType();
        //
        // // (注意：GEP 的第一个索引是针对 PointeeType 的)
        // for (int i = 1; i < indexes.size(); i++) {
        //     // 每次索引，我们就深入一层
        //     if (currentType instanceof ArrayType) {
        //         currentType = ((ArrayType) currentType).getElementType();
        //     } else {
        //         // (如果是结构体，会更复杂)
        //         // (但我们这里只有数组)
        //     }
        // }
        //
        // // 最终返回的是指向那个深层类型的指针
        // return PointerType.get(currentType);
    }

    public Value getPointer() { return this.getOperand(0); }

    @Override
    public boolean hasSideEffect() { return false; } // GEP 只是计算，无副作用

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装索引字符串
        // //    例如: ", i32 0, i32 %i"
        // String indexStr = "";
        // for (int i = 1; i < getNumOperands(); i++) {
        //     Value idx = this.getOperand(i);
        //     indexStr += ", " + idx.getType().toString() + " " + idx.getName();
        // }
        //
        // // 2. 拼装 GEP 字符串
        // //    例如: "%ptr = getelementptr inbounds i32, i32* %a, i32 0"
        // //    或:   "%e = getelementptr inbounds [10 x i32], [10 x i32]* %arr, i32 0, i32 %i"
        //
        // Value ptr = this.getPointer();
        // Type pointeeType = ((PointerType) ptr.getType()).getPointeeType();
        //
        // return this.getName() + " = getelementptr inbounds " +
        //        pointeeType.toString() + ", " +
        //        ptr.getType().toString() + " " + ptr.getName() +
        //        indexStr;
    }
}
