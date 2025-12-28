package middle.component.model;

import middle.component.inst.AllocInst;
import middle.component.inst.Instruction;
import middle.component.inst.TerminatorInst;
import middle.component.type.LabelType;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 基本块 (BasicBlock)。
 * 基本块是指令 (Instruction) 的容器。
 * 它本身也是一个 Value (因为 `br` 指令需要跳转到它)。
 */
public class BasicBlock extends Value {

    private Function parent;
    private final LinkedList<Instruction> instructions;
    private boolean isDeleted = false; // 标记是否被删除 (优化用)

    // ==========================================
    // 优化所需字段 (CFG & Dominator Tree)
    // ==========================================

    // CFG (控制流图) 信息
    private List<BasicBlock> nextBlocks = new ArrayList<>(); // 后继块 (Successors)
    private List<BasicBlock> prevBlocks = new ArrayList<>(); // 前驱块 (Predecessors)

    // 支配树信息 (Mem2Reg 需要)
    private BasicBlock immediateDominator; // 直接支配者 (IDom)
    private List<BasicBlock> dominateBlocks = new ArrayList<>(); // 支配的所有块
    private List<BasicBlock> immediateDominateBlocks = new ArrayList<>(); // 支配树中的直接子节点
    private List<BasicBlock> dominanceFrontier = new ArrayList<>(); // 支配边界 (DF)
    private int imdomDepth = 0; // 支配树深度

    // 循环信息 (可选，未来循环优化可能用到)
    private int loopDepth = 0;

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
     * 由 IRBuilder 或 优化Pass 调用，负责建立双向链接并维护指令顺序约束
     */
    public void addInstruction(Instruction inst) {
        // 0. 设置父指针
        inst.setParent(this);

        // --- 情况 1：Phi 指令 (PhiInst) ---
        // 必须严格位于基本块的最前端，甚至在 Alloc 之前（通常 Phi 和 Alloc 不会同时大量出现在非 Entry 块）
        // 如果你的 IR 设计允许 Alloc 在 Phi 之后，这里的逻辑是：Phi 插在所有非 Phi 之前。
        if (inst.getClass().getSimpleName().equals("PhiInst")) { // 使用类名判断，或者 inst instanceof PhiInst
            int index = 0;
            // 跳过已经存在的 Phi 指令，插在它们后面，但要在普通指令前面
            for (Instruction i : this.instructions) {
                if (!i.getClass().getSimpleName().equals("PhiInst")) {
                    break;
                }
                index++;
            }
            this.instructions.add(index, inst);
            return;
        }

        // --- 情况 2：Alloc 指令 (AllocInst) ---
        // 必须位于函数入口块的顶部（但在 Phi 之后）
        if (inst instanceof AllocInst) {
            int index = 0;
            for (Instruction i : this.instructions) {
                // 跳过 Phi 和 其他 Alloc，插在它们后面
                if (!i.getClass().getSimpleName().equals("PhiInst") && !(i instanceof AllocInst)) {
                    break;
                }
                index++;
            }
            this.instructions.add(index, inst);
            return;
        }

        // --- 情况 3：常规指令 & 终结者 ---

        if (this.instructions.isEmpty()) {
            this.instructions.add(inst);
        } else {
            Instruction lastInst = this.instructions.getLast();

            // 如果最后一条已经是终结者 (br/ret)
            if (lastInst instanceof TerminatorInst) {
                if (inst instanceof TerminatorInst) {
                    // 严重警告：试图向一个已有终结者的块添加另一个终结者
                    // 之前的逻辑是直接 return，这会掩盖 Bug。
                    // 正确的做法通常是：新的终结者应该替换旧的，或者这是逻辑错误。
                    // 这里为了安全起见，先移除旧的，再加新的（或者保持旧逻辑但打印警告）

                    // 建议方案：移除旧的，替换为新的（适应优化过程中的分支修改）
                    lastInst.setParent(null);
                    this.instructions.removeLast();
                    this.instructions.add(inst);
                } else {
                    // 普通指令插在终结者之前
                    this.instructions.add(this.instructions.size() - 1, inst);
                }
            } else {
                // 块还没有终结者，直接追加
                this.instructions.add(inst);
            }
        }
    }

    public Instruction getTerminator() {
        // 1. 检查块是否为空
        if (this.instructions.isEmpty()) {
            return null;
        }

        // 2. 获取最后一条指令
        Instruction lastInst = this.instructions.getLast();

        // 3. *关键*：检查它 *是否* 一个终结者
        if (lastInst instanceof TerminatorInst) {
            // 它是 BrInst 或 RetInst
            return lastInst;
        } else {
            // 它只是一个常规指令 (如 call, add, store)
            return null;
        }
    }

    // ==========================================
    // 为优化 (Mem2Reg/RegAlloc) 补充的方法
    // ==========================================

    /**
     * 获取第一条指令 (用于检查 Phi 指令)
     */
    public Instruction getFirstInstruction() {
        if (instructions.isEmpty()) return null;
        return instructions.getFirst();
    }

    /**
     * 获取最后一条指令 (用于获取 Br 指令构建 CFG)
     */
    public Instruction getLastInstruction() {
        if (instructions.isEmpty()) return null;
        return instructions.getLast();
    }

    // --- CFG Getter/Setter ---

    public List<BasicBlock> getNextBlocks() {
        return nextBlocks;
    }

    public void setNextBlocks(List<BasicBlock> nextBlocks) {
        this.nextBlocks = nextBlocks;
    }

    public List<BasicBlock> getPrevBlocks() {
        return prevBlocks;
    }

    public void setPrevBlocks(List<BasicBlock> prevBlocks) {
        this.prevBlocks = prevBlocks;
    }

    // --- Dominator Tree Getter/Setter ---

    public BasicBlock getImmediateDominator() {
        return immediateDominator;
    }

    public void setImmediateDominator(BasicBlock immediateDominator) {
        this.immediateDominator = immediateDominator;
    }

    public List<BasicBlock> getDominateBlocks() {
        return dominateBlocks;
    }

    public void setDominateBlocks(List<BasicBlock> dominateBlocks) {
        this.dominateBlocks = dominateBlocks;
    }

    public List<BasicBlock> getImmediateDominateBlocks() {
        return immediateDominateBlocks;
    }

    public void setImmediateDominateBlocks(List<BasicBlock> immediateDominateBlocks) {
        this.immediateDominateBlocks = immediateDominateBlocks;
    }

    public List<BasicBlock> getDominanceFrontier() {
        return dominanceFrontier;
    }

    public void setDominanceFrontier(List<BasicBlock> dominanceFrontier) {
        this.dominanceFrontier = dominanceFrontier;
    }

    public int getImdomDepth() {
        return imdomDepth;
    }

    public void setImdomDepth(int imdomDepth) {
        this.imdomDepth = imdomDepth;
    }

    public int getLoopDepth() {
        return loopDepth;
    }

    public void setLoopDepth(int loopDepth) {
        this.loopDepth = loopDepth;
    }

    // --- 标记删除相关 ---

    public boolean isDeleted() {
        return isDeleted;
    }

    public void setDeleted(boolean deleted) {
        isDeleted = deleted;
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

    /**
     * 克隆 BasicBlock 的外壳。
     * 注意：这不会克隆指令，指令的克隆和填充需要在 InlineFunction Pass 中
     * 配合 Instruction.copy() 和 valueMap 一起完成。
     *
     * @param newParent 该块所属的新函数（如果是内联，就是 Caller）
     * @return 新的空 BasicBlock
     */
    public BasicBlock cloneTo(Function newParent) {
        // 创建新块，名字为了调试方便可以加后缀，或者让 BasicBlock 构造函数自动重命名
        BasicBlock newBlock = new BasicBlock(this.getName() + "_inline", newParent);
        newBlock.setLoopDepth(this.loopDepth); // 复制一些基础属性
        return newBlock;
    }
}
