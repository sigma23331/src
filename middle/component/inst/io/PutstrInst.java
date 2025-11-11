package middle.component.inst.io;

import middle.component.model.Function;
import middle.component.model.Value;
import middle.component.inst.CallInst;
import java.util.ArrayList;

/**
 * @putstr(i8* value) 调用。
 */
public class PutstrInst extends CallInst {

    public PutstrInst(Function putstrFunc, Value stringPointer) {
        // // 提示：
        // // 同样使用静态辅助方法
        super("", putstrFunc, buildArgs(stringPointer));
    }

    /**
     * 静态辅助方法：在 super() 调用之前构建参数列表。
     */
    private static ArrayList<Value> buildArgs(Value stringPointer) {
        // // 提示：
        ArrayList<Value> args = new ArrayList<>();
        args.add(stringPointer);
        return args;
    }

    public Value getStringPointer() {
        return this.getArg(0);
    }
}
