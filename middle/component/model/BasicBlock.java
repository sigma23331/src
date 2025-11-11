package middle.component.model;

import middle.component.inst.Instruction; // (下一步)
import middle.component.type.LabelType; // (您需要创建这个 Type)
import java.util.LinkedList;
import java.util.stream.Collectors;

/**
 * 基本块 (BasicBlock)。
 * 基本块是指令 (Instruction) 的容器。
 * 它本身也是一个 Value (因为 `br` 指令需要跳转到它)。
 */
public class BasicBlock extends Value {

    private Function parent;
    private final LinkedList<Instruction> instructions;

    /**
     * 构造函数。
     */
    public BasicBlock(String name, Function parent) {
        super(LabelType.getInstance());
        this.setName(name);
        this.parent = parent;
        this.instructions = new LinkedList<>();

        if (parent != null) {
            parent.addBasicBlock(this);
        }
    }

    public Function getParent() { return this.parent; }
    public void setParent(Function parent) { this.parent = parent; }
    public LinkedList<Instruction> getInstructions() { return this.instructions; }

    /**
     * 由 IRBuilder 调用，负责建立双向链接
     */
    public void addInstruction(Instruction inst) {
        // // 提示：
        this.instructions.add(inst);
        inst.setParent(this); // 建立双向链接
    }

    public Instruction getTerminator() {
        // 提示：
        // "终结者"指令是 BB 的最后一条指令 (如 ret 或 br)
        return this.instructions.isEmpty() ? null : this.instructions.getLast();
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 打印标签 (例如 "entry:")
        // // 2. 打印所有指令 (inst.toString())
        //
        // // (注意：标签的第一行不缩进，指令缩进)
        String instStr = this.instructions.stream()
             .map(inst -> "\t" + inst.toString())
             .collect(Collectors.joining("\n"));
        // // (如果指令为空，可能需要一个占位符，例如一个 ret)
        return this.getName() + ":\n" + instStr;
    }
}
