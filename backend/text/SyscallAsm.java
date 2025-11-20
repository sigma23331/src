package backend.text;

import backend.enums.AsmOp;

public class SyscallAsm extends MipsInstruction {

    public SyscallAsm() {
        super(); // 自动加入指令流
    }

    @Override
    public String toString() {
        // 格式: syscall
        return AsmOp.SYSCALL.toString();
    }
}