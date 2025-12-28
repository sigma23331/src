package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.Value;
import middle.component.type.Type;

import java.util.ArrayList;
import java.util.List;

/**
 * PHI 指令 (phi)。
 * 用于在 SSA 形式中合并来自不同前驱基本块的值。
 */
public class PhiInst extends Instruction {

    /**
     * 原有的构造函数
     */
    public PhiInst(String name, Type type) {
        super(type);
        this.setName(name);
    }

    static int globalPhiCounter = 0;

    /**
     * (新) 构造函数：适配 Mem2Reg 优化的调用需求
     * @param type 数据类型 (例如 i32)
     * @param parentBlock 该指令所在的父基本块 (虽然指令通常后插入，但这里可以先接收)
     * @param predBlocks 前驱基本块列表 (构造时不一定立即填充操作数，通常在重命名阶段填充)
     */
    public PhiInst(Type type, BasicBlock parentBlock, List<BasicBlock> predBlocks) {
        super(type);
        // 【关键修复】：加上 "%" 前缀
        this.setName("%phi_" + (globalPhiCounter++));
    }

    /**
     * (新) 适配 Mem2Reg 的 addValue 方法
     * Mem2Reg 代码中使用了 phi.addValue(block, value)，参数顺序通常是 (块, 值)
     */
    public void addValue(BasicBlock fromBlock, Value value) {
        // 委托给 addIncoming，注意参数顺序调整
        addIncoming(value, fromBlock);
    }

    /**
     * 向 PHI 节点添加一个“传入”分支。
     * LLVM IR 标准格式: [ value, block ]
     * @param value 来自该分支的值
     * @param fromBlock 该分支的 BasicBlock
     */
    public void addIncoming(Value value, BasicBlock fromBlock) {
        // 1. Phi 指令的操作数总是成对出现的 (Value, BasicBlock)
        this.addOperand(value);
        this.addOperand(fromBlock);
    }

    /**
     * 获取传入分支的数量
     */
    public int getNumIncoming() {
        // 操作数是成对的
        return this.getNumOperands() / 2;
    }

    /**
     * 获取第 i 个传入的值
     */
    public Value getIncomingValue(int i) {
        return this.getOperand(i * 2);
    }

    /**
     * 获取第 i 个传入的块
     */
    public BasicBlock getIncomingBlock(int i) {
        return (BasicBlock) this.getOperand(i * 2 + 1);
    }

    /**
     * 获取该 Phi 指令涉及的所有前驱块列表
     * (RegAlloc 可能需要用到)
     */
    public List<BasicBlock> getBlocks() {
        List<BasicBlock> blocks = new ArrayList<>();
        for (int i = 0; i < getNumIncoming(); i++) {
            blocks.add(getIncomingBlock(i));
        }
        return blocks;
    }

    /**
     * 获取该 Phi 指令涉及的所有传入值列表
     */
    public List<Value> getIncomingValues() {
        List<Value> values = new ArrayList<>();
        for (int i = 0; i < getNumIncoming(); i++) {
            values.add(getIncomingValue(i));
        }
        return values;
    }

    /**
     * 移除第 i 个传入项 (Val, Block)
     */
    public void removeIncoming(int index) {
        // 操作数索引： value 是 index*2， block 是 index*2+1
        int operandIndex = index * 2;

        // 移除 Block (User 的 operands 列表)
        this.getOperands().remove(operandIndex + 1);
        // 移除 Value
        this.getOperands().remove(operandIndex);
    }



    @Override
    public boolean hasSideEffect() {
        return false; // Phi 节点只是选择，没有副作用
    }

    @Override
    public String toString() {
        StringBuilder incomingStr = new StringBuilder();
        for (int i = 0; i < getNumIncoming(); i++) {
            incomingStr.append("[ ");
            incomingStr.append(getIncomingValue(i).getName());
            incomingStr.append(", %");
            incomingStr.append(getIncomingBlock(i).getName());
            incomingStr.append(" ]");
            if (i < getNumIncoming() - 1) {
                incomingStr.append(", ");
            }
        }
        return this.getName() + " = phi " + this.getType().toString() + " " + incomingStr;
    }

    @Override
    public Instruction copy() {
        // 1. 创建一个新的空 Phi 指令
        // 我们使用第一个基础构造函数，保留原指令的名字（内联时通常会后续重命名）
        PhiInst newPhi = new PhiInst(this.getName(), this.getType());

        // 2. 遍历原指令的所有 [Value, BasicBlock] 对
        for (int i = 0; i < this.getNumIncoming(); i++) {
            Value val = this.getIncomingValue(i);
            BasicBlock block = this.getIncomingBlock(i);

            // 3. 将这些“旧”的操作数添加到新 Phi 中
            // 注意：这里添加的 block 还是旧函数的 BasicBlock。
            // 在 InlineFunction.performInline() 中，会调用 remapOperands()，
            // 那时会自动利用 valueMap 将这些旧 block 替换为新拷贝的 block。
            newPhi.addIncoming(val, block);
        }
        newPhi.setName(this.getName() + "_copy");
        return newPhi;
    }
}
