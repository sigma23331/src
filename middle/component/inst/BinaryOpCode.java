package middle.component.inst;

/**
 * 二元运算的操作码 (OpCode)。
 * * 关键区别：这是一个专用于二元运算的枚举。
 */
public enum BinaryOpCode {
    // 算术 (返回 i32)
    ADD,  // +
    SUB,  // -
    MUL,  // *
    SDIV, // / (有符号除法)
    SREM, // % (有符号取余)

    // 比较 (返回 i1)
    EQ,   // == (icmp eq)
    NE,   // != (icmp ne)
    SGT,  // >  (icmp sgt)
    SGE,  // >= (icmp sge)
    SLT,  // <  (icmp slt)
    SLE;  // <= (icmp sle)

    /**
     * 转换成 LLVM IR 关键字
     */
    @Override
    public String toString() {
        return switch (this) {
            case EQ -> "icmp eq";
            case NE -> "icmp ne";
            case SGT -> "icmp sgt";
            case SGE -> "icmp sge";
            case SLT -> "icmp slt";
            case SLE -> "icmp sle";
            default -> this.name().toLowerCase();
        };
    }

    /**
     * 检查是否是比较操作
     */
    public boolean isCompare() {
        // 检查 this.ordinal() 是否在 EQ 和 SLE 之间
        return this.ordinal() >= EQ.ordinal();
    }
}