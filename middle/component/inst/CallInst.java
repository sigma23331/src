package middle.component.inst;

import backend.enums.Register; // 引入后端的寄存器枚举
import middle.component.model.Function;
import middle.component.model.Value;
import middle.component.type.VoidType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 函数调用 (call) 指令。
 */
public class CallInst extends Instruction {

    // ==========================================
    // 后端优化所需字段 (RegAlloc 结果)
    // ==========================================
    /**
     * 在此调用点活跃的物理寄存器集合。
     * 后端需要根据此集合生成 Caller-Save 代码。
     */
    private Set<Register> activeReg = new HashSet<>();

    /**
     * 构造函数。
     */
    public CallInst(String name, Function function, ArrayList<Value> args) {
        super(function.getReturnType());
        // 预留 N 个参数 + 1 个函数本身的位置
        this.reserveOperands(args.size() + 1);

        if (!(function.getReturnType() instanceof VoidType)) {
            this.setName(name);
        }

        this.setOperand(0, function);
        for (int i = 0; i < args.size(); i++) {
            this.setOperand(i + 1, args.get(i));
        }
    }

    public Function getFunction() {
        return (Function) this.getOperand(0);
    }

    public int getNumArgs() {
        return this.getNumOperands() - 1;
    }

    public Value getArg(int i) {
        return this.getOperand(i + 1);
    }

    // ==========================================
    // 后端接口实现
    // ==========================================

    public void setActiveReg(Set<Register> activeReg) {
        this.activeReg = activeReg;
    }

    public Set<Register> getActiveReg() {
        return this.activeReg;
    }

    @Override
    public boolean hasSideEffect() {
        return true;
    }

    @Override
    public String toString() {
        String argsStr = "";
        for (int i = 0; i < getNumArgs(); i++) {
            Value arg = this.getArg(i);
            argsStr += arg.getType().toString() + " " + arg.getName();
            if (i < getNumArgs() - 1) argsStr += ", ";
        }
        Function func = this.getFunction();
        String call = "call " + func.getReturnType().toString() + " @" +
                func.getName() + "(" + argsStr + ")";
        if (this.getType() instanceof VoidType) {
            return call;
        } else {
            return this.getName() + " = " + call;
        }
    }

    @Override
    public Instruction copy() {
        // 1. 提取当前指令的所有参数
        // CallInst 的 Operand[0] 是函数本身，Operand[1...N] 是参数
        // 我们利用 getNumArgs() 和 getArg(i) 辅助方法来提取
        ArrayList<Value> args = new ArrayList<>();
        for (int i = 0; i < this.getNumArgs(); i++) {
            args.add(this.getArg(i));
        }

        // 2. 调用构造函数创建副本
        // 注意：新指令的操作数最初指向旧的 Value，后续在内联过程中会通过 remapOperands 更新
        CallInst inst = new CallInst(this.getName(), this.getFunction(), args);
        inst.setName(this.getName() + "_copy");
        return inst;
    }
}
