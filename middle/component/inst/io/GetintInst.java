package middle.component.inst.io;

import middle.component.model.Function;
import middle.component.inst.CallInst;
import java.util.ArrayList;

/**
 * @getint() 调用。
 * * 关键区别：继承 CallInst，构造函数接收 Function 对象。
 */
public class GetintInst extends CallInst {

    /**
     * 构造函数
     * @param name 结果名 (例如 %v1)
     * @param getintFunc 从符号表查找到的 @getint 函数
     */
    public GetintInst(String name, Function getintFunc) {
        // // 提示：
        // // 1. 调用父类 (CallInst) 的构造函数
        // // 2. @getint 没有参数
        super(name, getintFunc, new ArrayList<>());
    }
}
