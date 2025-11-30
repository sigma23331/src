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

    /**
     * (新) 构造函数：适配 Mem2Reg 优化的调用需求
     * @param type 数据类型 (例如 i32)
     * @param parentBlock 该指令所在的父基本块 (虽然指令通常后插入，但这里可以先接收)
     * @param predBlocks 前驱基本块列表 (构造时不一定立即填充操作数，通常在重命名阶段填充)
     */
    public PhiInst(Type type, BasicBlock parentBlock, List<BasicBlock> predBlocks) {
        super(type);
        // 给一个默认名字，例如 "phi"
        this.setName("phi");

        // Mem2Reg 在 placePhiNodes 阶段创建 Phi 时，
        // 具体的 incoming value 还没有确定 (需要等到 rename 阶段)。
        // 所以这里我们只需要初始化对象即可。
        // 操作数会在后续调用 addValue / addIncoming 时添加。
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
}
