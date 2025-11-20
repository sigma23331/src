package backend.text;

import backend.enums.AsmOp;
import backend.enums.Register;
import java.util.ArrayList;

public class JumpAsm extends MipsInstruction {
    private final AsmOp jumpOp;
    private final String jumpLabel; // j, jal 的目标
    private final Register jumpReg; // jr 的目标

    // 为了兼容 MipsBuilder 的优化逻辑（虽然 toString 暂时没用到，但逻辑层需要）
    // 重命名变量以去重
    private ArrayList<MemAsm> contextLoadInsts = new ArrayList<>();
    private ArrayList<MemAsm> contextStoreInsts = new ArrayList<>();

    /**
     * 标签跳转构造函数 (j, jal)
     */
    public JumpAsm(AsmOp op, String target) {
        super();
        this.jumpOp = op;
        this.jumpLabel = target;
        this.jumpReg = null;
    }

    /**
     * 寄存器跳转构造函数 (jr)
     */
    public JumpAsm(AsmOp op, Register reg) {
        super();
        this.jumpOp = op;
        this.jumpReg = reg;
        this.jumpLabel = null;
    }

    public AsmOp getOp() {
        return jumpOp;
    }

    public String getTarget() {
        return jumpLabel;
    }

    // 保持方法签名与 MipsBuilder 兼容，但内部使用新变量名
    public ArrayList<MemAsm> getLoadWords() {
        return contextLoadInsts;
    }

    public void setLoadWords(ArrayList<MemAsm> loadWords) {
        // 使用深拷贝或直接赋值，这里逻辑保持一致
        this.contextLoadInsts = new ArrayList<>(loadWords);
    }

    public ArrayList<MemAsm> getStoreWords() {
        return contextStoreInsts;
    }

    public void setStoreWords(ArrayList<MemAsm> storeWords) {
        this.contextStoreInsts = new ArrayList<>(storeWords);
    }

    @Override
    public String toString() {
        // 逻辑变更：通过判断 jumpReg 是否存在来决定输出格式，而非判断 OpCode
        // 这在功能上等价，但代码结构不同
        if (jumpReg != null) {
            // 对应 jr $ra
            return jumpOp.toString() + " " + jumpReg;
        } else {
            // 对应 j label 或 jal label
            return jumpOp.toString() + " " + jumpLabel;
        }
    }
}