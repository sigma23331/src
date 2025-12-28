package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.VoidType;

/**
 * 返回指令 (ret)。
 */
public class RetInst extends TerminatorInst {

    /**
     * 构造函数 (用于 ret <value>)
     */
    public RetInst(Value value) {
        // 提示：
        super(VoidType.getInstance()); // 1 个操作数 (返回值)
        this.reserveOperands(1);
        this.setOperand(0, value);
    }

    /**
     * 构造函数 (用于 ret void)
     */
    public RetInst() {
        // 提示：
        super(VoidType.getInstance()); // 0 个操作数
    }

    public boolean isVoidRet() {
        return this.getNumOperands() == 0;
    }

    public Value getReturnValue() {
        return this.isVoidRet() ? null : this.getOperand(0);
    }

    @Override
    public String toString() {
        // // 提示：
        if (this.isVoidRet()) {
            return "ret void";
        } else {
            Value retVal = this.getReturnValue();
            return "ret " + retVal.getType().toString() + " " + retVal.getName();
        }
    }

    @Override
    public Instruction copy() {
        if (this.isVoidRet()) {
            // 情况 1: ret void
            return new RetInst();
        } else {
            // 情况 2: ret <ty> <value>
            // 获取旧的操作数，传入构造函数。
            // 稍后 InlineFunction 会调用 remapOperands 将其替换为新值。
            return new RetInst(this.getReturnValue());
        }
    }
}
