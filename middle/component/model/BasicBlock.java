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
     * 由 IRBuilder 调用，负责建立双向链接
     */
    public void addInstruction(Instruction inst) {

        if (inst instanceof AllocInst) {
            // --- 情況 1：這是一條 alloca 指令 ---
            // * 必須 * 插入到入口塊的頂部（在所有非 alloca 之後）

            // // 提示：
            // // 1. 遍歷指令列表，找到第一個 *不是* AllocInst 的指令
            int index = 0;
            for (Instruction i : this.instructions) {
                if (!(i instanceof AllocInst)) {
                    break;
                }
                index++;
            }

            // // 2. 在該位置插入 alloca
            this.instructions.add(index, inst);

        } else {
            // --- 情況 2：這是一條常規指令 (add, load, br, ret...) ---

            if (this.instructions.isEmpty()) {
                // a. 塊是空的，直接添加
                this.instructions.add(inst);
            } else {
                // b. 塊不是空的，檢查最後一條指令
                Instruction lastInst = this.instructions.getLast();

                if (lastInst instanceof TerminatorInst) {
                    if (inst instanceof TerminatorInst) {
                        return; // (!!!)
                    } else {
                        // 插在终结者之前，保证基本块性质
                        this.instructions.add(this.instructions.size() - 1, inst);
                    }
                } else {
                    // d. 塊還沒有終結者，安全地添加到末尾
                    this.instructions.add(inst);
                }
            }
        }

        // * 關鍵 *：無論在哪裡插入，都設置父指針
        inst.setParent(this);
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
}
