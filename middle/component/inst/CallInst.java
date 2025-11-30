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
}
