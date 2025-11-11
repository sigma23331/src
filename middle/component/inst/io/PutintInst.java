package middle.component.inst.io;

import middle.component.model.Function;
import middle.component.model.Value;
import middle.component.inst.CallInst;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @putint(i32 value) 调用。
 * * 关键区别：继承 CallInst，构造函数接收 Function 对象和参数。
 */
/**
 * @putint(i32 value) 调用。
 */
public class PutintInst extends CallInst {

    /**
     * 构造函数
     * @param putintFunc 从符号表查找到的 @putint 函数
     * @param valueToPrint 要打印的 i32 Value
     */
    public PutintInst(Function putintFunc, Value valueToPrint) {
        // // 提示：
        // // 1. 调用 super() 必须是第一行。
        // //    我们使用一个静态辅助方法来准备参数列表。
        super("", putintFunc, buildArgs(valueToPrint));
    }

    /**
     * 静态辅助方法：在 super() 调用之前构建参数列表。
     */
    private static ArrayList<Value> buildArgs(Value valueToPrint) {
        // // 提示：
        ArrayList<Value> args = new ArrayList<>();
        args.add(valueToPrint);
        return args;
    }

    public Value getValueToPrint() {
        return this.getArg(0); // (第 0 个参数)
    }
}
