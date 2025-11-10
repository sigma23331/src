package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.Value;
import middle.component.type.VoidType;

/**
 * 分支指令 (br)。
 */
public class BrInst extends TerminatorInst {

    /**
     * 构造函数 (用于无条件跳转：br label %dest)
     */
    public BrInst(BasicBlock dest) {
        // // 提示：
        super(VoidType.getInstance(), 1); // 1 个操作数 (目标 BB)
        this.setOperand(0, dest);
    }

    /**
     * 构造函数 (用于条件跳转：br i1 %cond, label %ifTrue, label %ifFalse)
     */
    public BrInst(Value cond, BasicBlock ifTrue, BasicBlock ifFalse) {
        // // 提示：
        super(VoidType.getInstance(), 3); // 3 个操作数 (cond, true, false)
        this.setOperand(0, cond);
        this.setOperand(1, ifTrue);
        this.setOperand(2, ifFalse);
    }

    public boolean isConditional() {
        return this.getNumOperands() == 3;
    }

    // --- Getters for Operands ---
    public Value getCondition() { return isConditional() ? this.getOperand(0) : null; }
    public Value getTrueDest() { return isConditional() ? this.getOperand(1) : this.getOperand(0); }
    public Value getFalseDest() { return isConditional() ? this.getOperand(2) : null; }

    @Override
    public String toString() {
        // // 提示：
        // // (注意：BasicBlock 作为 Value，它的名字就是标签名)
        if (this.isConditional()) {
            return "br " + this.getCondition().getType().toString() + " " +
                   this.getCondition().getName() + ", label %" +
                   this.getTrueDest().getName() + ", label %" +
                   this.getFalseDest().getName();
        } else {
            return "br label %" + this.getTrueDest().getName();
        }
    }
}
