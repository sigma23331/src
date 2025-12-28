package optimize;

import middle.component.inst.*;
import middle.component.model.*;
import middle.component.type.VoidType;
import middle.component.model.Module; // 防止冲突

import java.util.*;

public class InlineFunction {

    // 最大内联深度/轮数，防止无限循环
    private static final int MAX_PASSES = 10;
    // 最大指令数阈值，超过这个大小的函数不内联 (防止代码膨胀)
    private static final int MAX_INST_COUNT = 300;
    private static int inlineUniqueId = 0;

    public static void run(Module module) {
        boolean changed = true;
        int pass = 0;

        // 迭代多次，因为内联 A 后，A 内部可能暴露出了对 B 的调用，B 也可能被内联
        while (changed && pass < MAX_PASSES) {
            changed = false;
            pass++;

            // 收集所有非声明函数
            List<Function> funcs = new ArrayList<>();
            for (Function f : module.getFunctions()) {
                if (!f.isDeclaration()) {
                    funcs.add(f);
                }
            }

            for (Function func : funcs) {
                // 对每个函数执行内联扫描
                changed |= inlineCallsInFunction(func);
            }
        }
    }

    /**
     * 扫描函数中的所有 Call 指令，尝试内联
     */
    private static boolean inlineCallsInFunction(Function caller) {
        boolean changed = false;
        boolean localChanged = true;

        // 【保险栓】限制单个函数内部的内联轮数，防止意外的死循环
        int innerLoopLimit = 0;

        while (localChanged && innerLoopLimit < 10) { // 限制最多重试 10 次
            localChanged = false;
            innerLoopLimit++; // 计数

            List<BasicBlock> blocks = new ArrayList<>(caller.getBasicBlocks());

            for (BasicBlock bb : blocks) {
                List<Instruction> insts = new ArrayList<>(bb.getInstructions());
                for (Instruction inst : insts) {
                    if (inst instanceof CallInst call) {
                        Function callee = call.getFunction();

                        if (shouldInline(caller, callee)) {
                            performInline(caller, call, callee);
                            changed = true;
                            localChanged = true;
                            break;
                        }
                    }
                }
                if (localChanged) break;
            }
        }
        return changed;
    }

    /**
     * 内联策略判断
     */
    private static boolean shouldInline(Function caller, Function callee) {
        // 1. 必须是定义好的函数，且不是库函数
        if (callee.isDeclaration()) return false;

        // 2. 禁止直接递归 (caller == callee)
        if (caller == callee) return false;

        // 【新增修复】: 如果 Callee 内部有递归调用，绝对不能内联！
        // 否则会内联到 Caller 里导致无限展开
        if (isRecursive(callee)) return false;

        // 3. main 函数不被内联
        if (callee.getName().equals("main")) return false;

        // 4. 计算指令数量，太大的不内联
        int instCount = 0;
        for (BasicBlock bb : callee.getBasicBlocks()) {
            instCount += bb.getInstructions().size();
        }
        // 建议把阈值稍微调小一点，太大的函数内联收益低风险大
        if (instCount > MAX_INST_COUNT) return false;

        return true;
    }

    /**
     * 核心逻辑：执行内联
     * @param caller 调用者函数
     * @param callInst 调用指令 (将被移除)
     * @param callee 被调用函数
     */
    private static void performInline(Function caller, CallInst callInst, Function callee) {
        // [步骤 1] 拆分基本块
        BasicBlock callBB = callInst.getParent();
        BasicBlock splitBB = splitBlock(callBB, callInst, caller);

        // [步骤 2] 建立映射表
        Map<Value, Value> valueMap = new HashMap<>();

        // [步骤 3] 映射参数
        for (int i = 0; i < callee.getParams().size(); i++) {
            valueMap.put(callee.getParams().get(i), callInst.getArg(i));
        }

        // ---------------------------------------------------------
        // 【修改点】获取当前唯一的内联ID，并自增
        // ---------------------------------------------------------
        int currentInlineId = inlineUniqueId++;

        // [步骤 4] 克隆基本块 (Clone Blocks)
        List<BasicBlock> newBlocks = new ArrayList<>();
        for (BasicBlock oldBB : callee.getBasicBlocks()) {
            BasicBlock newBB = oldBB.cloneTo(caller);

            // 【修改点】强制重命名，加上唯一后缀
            // 例如: "for.cond" -> "for.cond_inline_5"
            // 这样即使多次内联同一个函数，名字也不会冲突
            newBB.setName(oldBB.getName() + "_inline_" + currentInlineId);

            valueMap.put(oldBB, newBB);
            newBlocks.add(newBB);
        }

        // [步骤 5] 克隆指令 (Clone Instructions)
        for (BasicBlock oldBB : callee.getBasicBlocks()) {
            BasicBlock newBB = (BasicBlock) valueMap.get(oldBB);

            for (Instruction oldInst : oldBB.getInstructions()) {
                Instruction newInst = oldInst.copy();

                // 【建议】为了防止指令名冲突 (虽然 LLVM IR 允许重名，但为了调试清晰)
                // 也可以给指令名加后缀
                if (newInst.getName() != null) {
                    // newInst.setName(newInst.getName() + "_" + currentInlineId);
                }

                if (!(newInst.getType() instanceof VoidType)) {
                    valueMap.put(oldInst, newInst);
                }

                // 确保加入到新块
                newInst.setParent(newBB);
                newBB.getInstructions().add(newInst);
            }
        }

        // [步骤 6] 重映射操作数 (Remap Operands)
        // 遍历所有新指令，把它们的操作数从“旧Value”替换为“新Value”
        for (BasicBlock newBB : newBlocks) {
            for (Instruction inst : newBB.getInstructions()) {
                inst.remapOperands(valueMap);
            }
        }

        // [步骤 7] 缝合入口 (Stitch Entry)
        // callBB (原块前半段) -> Callee 的新 Entry
        BasicBlock calleeEntry = (BasicBlock) valueMap.get(callee.getEntryBlock());
        // 在 callBB 末尾加跳转
        // 注意：callInst 还在 callBB 里，我们在最后才删它，现在先插在它前面或后面都行
        // 但因为 callBB 已经被切分，callInst 是最后一条，所以可以直接移除 callInst 并加 Br
        callInst.remove();
        new BrInst(calleeEntry).setParent(callBB);
        callBB.getInstructions().add(new BrInst(calleeEntry)); // 手动添加，因为 remove 可能会清空 parent


        // [Step 8] 缝合出口 (Stitch Exits)
        List<AbstractMap.SimpleEntry<Value, BasicBlock>> returnValues = new ArrayList<>();

        for (BasicBlock newBB : newBlocks) {
            // 获取最后一条指令
            Instruction term = newBB.getTerminator();

            // 使用 instanceof 强类型判断
            // 请确认你的 Return 指令类名是 RetInst 还是 ReturnInst
            if (term instanceof RetInst) {
                RetInst ret = (RetInst) term;

                // 1. 收集返回值 (如果有)
                if (ret.getNumOperands() > 0) {
                    // 注意：这里获取的是 ret 的操作数，它应该已经在 Step 6 中被 remap 过了
                    // 如果 Step 6 没跑，这里拿到的还是旧 Value，那是错的。
                    // 所以 Step 6 (Remap) 必须在 Step 8 之前。
                    returnValues.add(new AbstractMap.SimpleEntry<>(ret.getOperand(0), newBB));
                }

                // 2. 移除 Ret
                ret.remove();

                // 3. 插入跳转到 splitBB
                BrInst br = new BrInst(splitBB);
                br.setParent(newBB);
                newBB.getInstructions().add(br);
            }
        }

        // [Step 9] 处理返回值
        if (!returnValues.isEmpty()) {
            Value finalRetVal;

            if (returnValues.size() == 1) {
                // 只有一个返回点 (比如 simple_add)
                finalRetVal = returnValues.get(0).getKey();
            } else {
                // 多个返回点 (比如 abs_val) -> 生成 Phi
                PhiInst phi = new PhiInst(callInst.getType(), splitBB, new ArrayList<>());

                // 填充 Phi
                for (var entry : returnValues) {
                    phi.addIncoming(entry.getKey(), entry.getValue());
                }

                // 【关键】插在 splitBB 最前面
                if (splitBB.getInstructions().isEmpty()) {
                    splitBB.getInstructions().add(phi);
                } else {
                    splitBB.getInstructions().addFirst(phi);
                }
                // 别忘了设置 parent！
                phi.setParent(splitBB);

                finalRetVal = phi;
            }

            // 【至关重要】替换原 Call 的所有使用者
            // 如果这一步没做，%v5, %v10 这种未定义变量就会残留！
            callInst.replaceAllUsesWith(finalRetVal);
        }
    }

    /**
     * 辅助方法：拆分基本块
     * 在 splitPoint 处将 oldBB 切开，splitPoint 之后的内容移到新块。
     * @return 新生成的后半部分块 (splitBB)
     */
    private static BasicBlock splitBlock(BasicBlock oldBB, Instruction splitPoint, Function func) {
        BasicBlock newBB = new BasicBlock(oldBB.getName() + "_split", func);

        // 移动指令
        List<Instruction> oldInsts = oldBB.getInstructions();
        List<Instruction> moveList = new ArrayList<>();

        boolean startMoving = false;
        for (Instruction inst : oldInsts) {
            if (inst == splitPoint) {
                startMoving = true;
                continue; // splitPoint (CallInst) 暂时留在原块，稍后手动处理
            }
            if (startMoving) {
                moveList.add(inst);
            }
        }

        // 执行移动
        for (Instruction inst : moveList) {
            oldInsts.remove(inst);
            // 修正 inst 的 parent 指针
            // 我们可以利用 addInstruction 自动处理，也可以手动处理
            // 为防止 addInstruction 的自动插入逻辑（如 Alloc 提头）干扰顺序，
            // 这里建议直接操作 list 并 setParent
            newBB.getInstructions().add(inst);
            inst.setParent(newBB);
        }

        // 维护 CFG 关系 (Successors)
        // 原块的后继现在变成了新块的后继
        newBB.setNextBlocks(new ArrayList<>(oldBB.getNextBlocks()));
        oldBB.getNextBlocks().clear();

        // 修正后继块的 Predecessors 指针
        for (BasicBlock succ : newBB.getNextBlocks()) {
            succ.getPrevBlocks().remove(oldBB);
            succ.getPrevBlocks().add(newBB);

            // 【非常重要】如果后继块里有 Phi 节点， Phi 里的 oldBB 必须改成 newBB
            for (Instruction inst : succ.getInstructions()) {
                if (inst instanceof PhiInst phi) {
                    for (int i = 0; i < phi.getNumIncoming(); i++) {
                        if (phi.getIncomingBlock(i) == oldBB) {
                            // 这里我们没有 setIncomingBlock 的接口，只能先取出来再改 operand
                            // Phi 的 Block 在奇数位 (1, 3, ...)
                            phi.setOperand(i * 2 + 1, newBB);
                        }
                    }
                } else {
                    break; // Phi 都在头部
                }
            }
        }

        // 原块现在的后继是新块 (逻辑上)，但我们稍后会插入指令跳转到 Callee
        // 所以这里暂时不用连接 oldBB -> newBB，因为中间隔着 Callee

        return newBB;
    }

    /**
     * 检测函数是否包含直接递归调用 (自己调自己)
     */
    private static boolean isRecursive(Function func) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            for (Instruction inst : bb.getInstructions()) {
                if (inst instanceof CallInst call) {
                    if (call.getFunction() == func) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
