package middle.component.inst;

import middle.component.model.Value;
import middle.component.type.IntegerType;
import middle.component.type.Type;

/**
 * 二元运算指令 (例如 add, sub, mul, icmp eq)。
 */
public class BinaryInst extends Instruction {

    private final BinaryOpCode opCode;

    /**
     * 构造函数。
     * * 关键区别：使用 setOperand，而不是 addOperand。
     */
    public BinaryInst(BinaryOpCode opCode, Value op1, Value op2) {
        super(opCode.isCompare() ? IntegerType.get1() : op1.getType());
        this.reserveOperands(2);
        this.opCode = opCode;
        //设置操作数
        this.setOperand(0,op1);
        this.setOperand(1,op2);
    }

    public Value getOp1() { return this.getOperand(0); }
    public Value getOp2() { return this.getOperand(1); }
    public BinaryOpCode getOpCode() { return this.opCode; }

    @Override
    public boolean hasSideEffect() {
        // 算术运算通常没有副作用 (除零是未定义行为，不是副作用)
        // * 关键区别：与您的源代码(true)不同
        return false;
    }

    @Override
    public String toString() {
        // 1. 拼装
        //    例如: "%v1 = add i32 %a, %b"
        //    或:   "%v2 = icmp eq i32 %a, 10"
        return this.getName() + " = " + this.opCode.toString() + " " +
               this.getOp1().getType().toString() + " " +
               this.getOp1().getName() + ", " +
               this.getOp2().getName(); // (i1 类型不需要打印类型)
    }

    @Override
    public Instruction copy() {
        BinaryInst inst = new BinaryInst(this.getOpCode(),this.getOp1(),this.getOp2());
        inst.setName(this.getName() + "_copy");
        return inst;
    }


}
