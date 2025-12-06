package optimize;

import middle.component.inst.*;
import middle.component.inst.io.GetintInst;
import middle.component.inst.io.PutintInst;
import middle.component.inst.io.PutstrInst;
import middle.component.model.*;
import middle.component.model.Module;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;

public class DeadCodeElimination {

    public static void run(Module module) {
        // 1. 简单的迭代删除 (处理 UseList 为空的情况)
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Function func : module.getFunctions()) {
                if (func.isDeclaration()) continue;
                for (BasicBlock block : func.getBasicBlocks()) {
                    // 使用 removeIf 安全删除
                    // removeIf 返回 true 表示有元素被删除
                    if (block.getInstructions().removeIf(DeadCodeElimination::isTriviallyDead)) {
                        changed = true;
                    }
                }
            }
        }

        // 2. 基于活跃性的删除 (Mark-Sweep)
        // 处理循环依赖死代码 (如 a=b+1; b=a+1; 但没人用 a,b)
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            removeUnusedCode(func);
        }
    }

    /**
     * 判断指令是否是显而易见的死代码
     * 条件：无副作用 且 无人使用
     */
    private static boolean isTriviallyDead(Instruction inst) {
        // 有副作用的指令绝不能删 (Store, Call, Br, Ret, Put...)
        if (inst.hasSideEffect()) {
            // 特殊情况：无副作用的函数调用 (Pure Function) 可以删
            // 这里简单起见，假设所有 Call 都有副作用，除非你实现了 SideEffectAnalysis
            return false;
        }

        // 终结指令 (Br, Ret) 不能删
        // (虽然它们通常 hasSideEffect=false，但它们是控制流的关键)
        if (inst instanceof BrInst || inst instanceof RetInst) {
            return false;
        }

        // 如果没有副作用，且 UseList 为空，则是死代码
        if (inst.getUseList().isEmpty()) {
            inst.removeOperands(); // 断开 Use-Def 链
            return true;
        }

        return false;
    }

    /**
     * 基于活跃性分析的死代码消除 (Mark-Sweep)
     */
    private static void removeUnusedCode(Function func) {
        Set<Instruction> usefulInstructions = new HashSet<>();
        Stack<Instruction> workList = new Stack<>();

        // 1. Mark: 收集所有“根”有用指令
        for (BasicBlock block : func.getBasicBlocks()) {
            for (Instruction inst : block.getInstructions()) {
                if (isCritical(inst)) {
                    usefulInstructions.add(inst);
                    workList.push(inst);
                }
            }
        }

        // 2. Propagate: 反向标记依赖
        while (!workList.isEmpty()) {
            Instruction inst = workList.pop();
            // 遍历该指令使用的所有操作数
            for (Use operand : inst.getOperands()) {
                Value op = operand.getValue();
                if (op instanceof Instruction) {
                    Instruction opInst = (Instruction) op;
                    // 如果操作数是指令，且未被标记，则标记并加入队列
                    if (!usefulInstructions.contains(opInst)) {
                        usefulInstructions.add(opInst);
                        workList.push(opInst);
                    }
                }
            }
        }

        // 3. Sweep: 删除未标记的指令
        for (BasicBlock block : func.getBasicBlocks()) {
            block.getInstructions().removeIf(inst -> {
                if (!usefulInstructions.contains(inst)) {
                    inst.removeOperands(); // 彻底断开
                    return true; // 删除
                }
                return false; // 保留
            });
        }
    }

    /**
     * 判断指令是否是“关键指令”(Critical Instruction)
     * 关键指令包括：有副作用的指令、终结指令 (控制流)
     */
    private static boolean isCritical(Instruction inst) {
        // 1. 写内存 (Store)
        if (inst instanceof StoreInst) return true;

        // 2. IO 操作 / 函数调用
        // (假设所有 Call 都是关键的，除非有纯函数分析)
        if (inst instanceof CallInst) return true;
        if (inst instanceof PutintInst || inst instanceof PutstrInst || inst instanceof GetintInst) return true;

        // 3. 控制流 (Br, Ret)
        if (inst instanceof BrInst || inst instanceof RetInst) return true;

        // 4. MoveInst (如果涉及全局变量或作为 Phi 消除产物，通常需要保留)
        // 但在 SSA 中，Move 通常是临时的。
        // 为了安全，如果 Move 的目标被 Use 了，它会在 Mark 阶段被标记。
        // 这里主要关注那些必须保留的“源头”。

        return inst.hasSideEffect();
    }
}