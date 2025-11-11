package middle.component.inst;

import middle.component.model.Function;
import middle.component.model.Value;
import middle.component.type.VoidType;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * 函数调用 (call) 指令。
 * * 关键区别：
 * 1. 继承 Instruction (User)，使用 setOperand。
 * 2. 构造函数干净，不依赖全局状态。
 * 3. hasSideEffect() 修正为 true。
 * 4. 不包含后端 (activeReg) 信息。
 */
public class CallInst extends Instruction {

    /**
     * 构造函数。
     * @param name 结果名 (如果非 void)
     * @param function 被调用的 Function 对象
     * @param args 传递的参数列表
     */
    public CallInst(String name, Function function, ArrayList<Value> args) {
        // 1. 指令的 Type 是函数的返回类型
        super(function.getReturnType(), args.size() + 1);// N 个参数 + 1 个函数
        // 2. (如果不是 void) 设置结果名
        if (!(function.getReturnType() instanceof VoidType)) {
            this.setName(name);
        }
        // 3. 设置操作数 (操作数 0 是被调用的函数)
        this.setOperand(0,function);
        for (int i = 0;i < args.size();i++) {
            this.setOperand(i + 1, args.get(i));
        }
    }

    /**
     * 获取被调用的 Function 对象
     */
    public Function getFunction() {
        return (Function) this.getOperand(0);
    }

    /**
     * 获取参数数量
     */
    public int getNumArgs() {
        return this.getNumOperands() - 1;
    }

    /**
     * 按索引获取参数
     */
    public Value getArg(int i) {
        return this.getOperand(i + 1);
    }

    @Override
    public boolean hasSideEffect() {
        // // 提示：
        // // * 关键修正：函数调用 *总是* 被认为有副作用
        // // (它可能修改全局变量, I/O, 或其他)
        return true;
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装参数字符串 (与您的源代码逻辑相同)
        // //    例如: "(i32 %a, i8* @.str)"
        String argsStr = "";
        for (int i = 0; i < getNumArgs(); i++) {
            Value arg = this.getArg(i);
            argsStr += arg.getType().toString() + " " + arg.getName();
            if (i < getNumArgs() - 1) argsStr += ", ";
        }
        //
        // 2. 拼装 Call 字符串
        //    例如: "%v = call i32 @my_func(i32 %a)"
        //    或:   "call void @putint(i32 %v)"
        Function func = this.getFunction();
        String call = "call " + func.getReturnType().toString() + " @" +
                      func.getName() + "(" + argsStr + ")";
        if (this.getType() instanceof VoidType) {
            return call;
        } else {
            return this.getName() + " = " + call;
        }
    }
}
