package middle.component.inst;

import middle.component.model.BasicBlock;
import middle.component.model.Value;
import middle.component.type.Type;
import java.util.stream.Collectors;

/**
 * PHI 指令 (phi)。
 * 用于在 SSA 形式中合并来自不同前驱基本块的值。
 */
public class PhiInst extends Instruction {

    /**
     * 构造函数 (创建一个“空”的 phi)。
     * @param name 结果名
     * @param type 要合并的值的类型 (例如 i32)
     */
    public PhiInst(String name, Type type) {
        super(type);
        this.setName(name);
    }

    /**
     * (新) 向 PHI 节点添加一个“传入”分支。
     * * 关键方法 *
     * @param value 来自该分支的值 (例如 ConstInt.get(10))
     * @param fromBlock 该分支的 BasicBlock (例如 %if_true_block)
     */
    public void addIncoming(Value value, BasicBlock fromBlock) {
        // // 提示：
        // // 1. Phi 指令的操作数总是成对出现的 (Value, BasicBlock)
        // // 2. 我们使用 addOperand 动态添加
        this.addOperand(value);
        this.addOperand(fromBlock);
    }

    /**
     * 获取传入分支的数量
     */
    public int getNumIncoming() {
        // // 提示：
        // // 操作数是成对的
        return this.getNumOperands() / 2;
    }

    /**
     * 获取第 i 个传入的值
     */
    public Value getIncomingValue(int i) {
        // // 提示：
        return this.getOperand(i * 2);
    }

    /**
     * 获取第 i 个传入的块
     */
    public BasicBlock getIncomingBlock(int i) {
        // // 提示：
        return (BasicBlock) this.getOperand(i * 2 + 1);
    }

    @Override
    public boolean hasSideEffect() {
        return false; // Phi 节点只是选择，没有副作用
    }

    @Override
    public String toString() {
        // // 提示：
        // // 1. 拼装所有传入分支
        // //    例如: "[ 10, %if_true ], [ 20, %if_false ]"
        StringBuilder incomingStr = new StringBuilder();
        for (int i = 0;i < getNumIncoming(); i++) {
            incomingStr.append("[").append(getIncomingValue(i)).
                    append(", %").append(getIncomingBlock(i).getName()).append("]");
            if (i < getNumIncoming() - 1) {
                incomingStr.append(", ");
            }
        }
        // // 2. 拼装 Phi 指令
        // //    例如: "%a.phi = phi i32 [ 10, %if_true ], [ 20, %if_false ]"
        return this.getName() + " = phi " + this.getType().toString() + " " + incomingStr;
    }
}
