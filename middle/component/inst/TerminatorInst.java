package middle.component.inst;

import middle.component.type.Type;

/**
 * 终结者指令的抽象基类。
 * 每个 BasicBlock 的最后一条指令必须是 TerminatorInst。
 * * 关键区别：这是一个新的抽象层。
 */
public abstract class TerminatorInst extends Instruction {

    public TerminatorInst(Type type, int numOperands) {
        super(type, numOperands);
    }

    @Override
    public boolean hasSideEffect() {
        // // 所有的终结者指令 (br, ret) 都会改变控制流，
        // // 它们被认为是有副作用的。
        return true;
    }
}
