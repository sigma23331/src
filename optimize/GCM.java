package optimize;

import middle.component.inst.*;
import middle.component.model.*;
import middle.component.model.Module;

import java.util.*;

public class GCM {

    // 记录指令 -> "最早能去的块"
    private static final Map<Instruction, BasicBlock> earlyBlockMap = new HashMap<>();

    // 记录已经访问过的指令，防止重复计算 (用于递归)
    private static final Set<Instruction> visited = new HashSet<>();

    // 记录指令 -> "最晚必须去的块"
    private static final Map<Instruction, BasicBlock> lateBlockMap = new HashMap<>();

    // 记录指令 -> "最终决定的块" (Step 3 会用到，先定义着)
    private static final Map<Instruction, BasicBlock> finalBlockMap = new HashMap<>();

    public static void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            runOnFunction(func);
        }
    }

    private static void runOnFunction(Function func) {
        // 1. 基础信息
        computeDomDepth(func.getEntryBlock(), 0);
        // 如果有循环分析，在这里运行 LoopAnalysis.run(func);

        earlyBlockMap.clear();
        lateBlockMap.clear();
        visited.clear();

        // 2. Early Schedule
        List<Instruction> allInsts = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) allInsts.addAll(bb.getInstructions());
        for (Instruction inst : allInsts) {
            if (inst.getParent() != null) scheduleEarly(inst, func.getEntryBlock());
        }

        // 3. Late Schedule
        scheduleLate(func);

        // 4. Global Placement (物理移动)
        placeInstructions(func);
    }

    // 在 runOnFunction 的 for-loop 之后调用这个
    private static void scheduleLate(Function func) {
        lateBlockMap.clear();
        visited.clear(); // 复用 visited 标记，这次用于标记是否已计算 Late

        // 1. 收集所有指令
        List<Instruction> allInsts = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            allInsts.addAll(bb.getInstructions());
        }

        // 2. 对每个 Pin 住的指令，先设定好 Late = Current
        // 这里的逻辑是：Pinned 指令其实 Early = Late = Current
        for (Instruction inst : allInsts) {
            if (isPinned(inst)) {
                visited.add(inst);
                lateBlockMap.put(inst, inst.getParent());
            }
        }

        // 3. 对其他指令计算 Late
        // 注意：和 Early 不同，Late 需要从 Users 反向推导。
        // 我们遍历所有指令，对未访问的调用递归 helper
        for (Instruction inst : allInsts) {
            if (!visited.contains(inst)) {
                findLate(inst);
            }
        }
    }

    /**
     * Schedule Late: 计算指令最晚必须在哪个块执行
     * 依据：指令必须支配所有它被使用的地方 (Dominates all uses)
     */
    private static BasicBlock findLate(Instruction inst) {
        if (visited.contains(inst)) {
            return lateBlockMap.get(inst);
        }
        visited.add(inst);

        BasicBlock lca = null;
        List<Use> uses = inst.getUseList();

        for (Use use : uses) {
            User user = use.getUser();
            if (!(user instanceof Instruction userInst)) continue;

            BasicBlock useBlock = null;

            // --- 特殊处理 Phi ---
            if (userInst instanceof PhiInst phi) {
                // 【修复点】：正确遍历 Phi 的 Incoming 分支
                // getNumIncoming() 返回的是分支对的数量 (例如 2)
                for (int i = 0; i < phi.getNumIncoming(); i++) {
                    // 检查第 i 个分支的 Value 是否是我们当前的指令
                    if (phi.getIncomingValue(i) == inst) {
                        // 如果是，获取该分支对应的 Block
                        BasicBlock incoming = phi.getIncomingBlock(i);
                        lca = findCommonDominator(lca, incoming);
                    }
                }
            } else {
                // --- 普通指令 ---
                BasicBlock userLate = findLate(userInst);
                lca = findCommonDominator(lca, userLate);
            }
        }

        if (lca == null) {
            lca = inst.getParent();
        }

        lateBlockMap.put(inst, lca);
        return lca;
    }

    /**
     * 【新增】寻找两个基本块在支配树上的最近公共祖先 (LCA)
     * 依赖：BasicBlock.getImdomDepth() 和 getImmediateDominator()
     */
    private static BasicBlock findCommonDominator(BasicBlock a, BasicBlock b) {
        if (a == null) return b;
        if (b == null) return a;

        // 1. 深度对齐：让较深的节点向上爬
        while (a.getImdomDepth() > b.getImdomDepth()) {
            a = a.getImmediateDominator();
        }
        while (b.getImdomDepth() > a.getImdomDepth()) {
            b = b.getImmediateDominator();
        }

        // 2. 同步上爬：直到相遇
        while (a != b) {
            a = a.getImmediateDominator();
            b = b.getImmediateDominator();
        }

        return a;
    }

    // 简单的深度计算辅助方法
    private static void computeDomDepth(BasicBlock block, int depth) {
        block.setImdomDepth(depth);
        for (BasicBlock child : block.getImmediateDominateBlocks()) {
            computeDomDepth(child, depth + 1);
        }
    }

    /**
     * Schedule Early 算法
     * 逻辑：一个指令必须等它的所有操作数都定义好了才能执行。
     * 所以，它的 EarlyBlock 是它所有操作数的定义块中，支配树深度最深的那个。
     */
    private static BasicBlock scheduleEarly(Instruction inst, BasicBlock entryBlock) {
        // 1. 如果已经计算过，直接返回
        if (visited.contains(inst)) {
            return earlyBlockMap.get(inst);
        }
        visited.add(inst);

        // 2. 如果指令是 "Pinned" (被钉住的)，它最早只能在它当前的位置
        // 或者说它不能被 GCM 移动，我们将其 Early 设为当前块
        if (isPinned(inst)) {
            earlyBlockMap.put(inst, inst.getParent());
            return inst.getParent();
        }

        // 3. 遍历所有操作数，找到支配树最深的那个 Block
        BasicBlock deepBlock = entryBlock; // 初始假设为入口块 (深度0)

        for (Use op : inst.getOperands()) {
            Value operand = op.getValue();
            BasicBlock opBlock = null;

            if (operand instanceof Instruction opInst) {
                // 如果操作数是指令，递归计算它的 EarlyBlock
                // 注意：这里其实可以用 opInst.getParent()，但标准算法建议递归 ScheduleEarly
                // 不过在 SSA 形式下，直接取操作数的定义块通常也可以，因为 Definition 必定支配 Use
                // 为了简单，我们先取操作数的 EarlyBlock (递归) 确保依赖链正确
                opBlock = scheduleEarly(opInst, entryBlock);
            }
            else if (operand instanceof BasicBlock) {
                // 比如 Phi 指令引用了 Block，但 Phi 是 Pinned，不会进到这里
                // Branch 指令引用了 Block，但 Branch 是 Pinned，也不会进这里
            }
            // 注意：ConstInt, GlobalVariable 等没有 Block，相当于定义在 Entry (深度0)

            // 更新最深的块 (DomDepth 越大越深)
            if (opBlock != null) {
                if (opBlock.getImdomDepth() > deepBlock.getImdomDepth()) {
                    deepBlock = opBlock;
                }
            }
        }

        // 4. 记录并返回
        earlyBlockMap.put(inst, deepBlock);
        return deepBlock;
    }

    /**
     * 判断指令是否被 "钉住" (Pinned)
     * 被钉住的指令有副作用或控制流依赖，不能移动。
     */
    private static boolean isPinned(Instruction inst) {
        // 1. 如果是二元运算，进一步检查是否是比较运算
        if (inst instanceof BinaryInst binary) {
            // 假设你的 BinaryOpCode 枚举里有比较相关的类型
            // 例如 EQ, NE, SGT, SLT, GE, LE 等
            // 如果是比较运算，我们暂时认为它 "Pinned"，防止它被提得太远
            if (isCompareOp(binary.getOpCode())) {
                return true; // 【关键修改】钉住比较指令
            }
            return false; // 加减乘除等算术指令允许移动
        }

        if (inst instanceof GepInst) {
            return false;
        }

        // 2. 剩下的通常都要钉住 (Load/Store/Call/Br/Ret/Phi)
        return true;
    }

    // 辅助方法：判断 OpCode 是否为比较运算
    private static boolean isCompareOp(BinaryOpCode op) {
        return switch (op) {
            case EQ, NE, SGT, SGE, SLT, SLE -> true;
            default -> false; // ADD, SUB, MUL, DIV ...
        };
    }

    /**
     * Step 3: 寻找最佳位置并移动指令
     */
    private static void placeInstructions(Function func) {
        // 遍历所有指令
        List<Instruction> allInsts = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            allInsts.addAll(bb.getInstructions());
        }

        // 【关键修复】反向遍历！
        // 因为我们的插入策略是 "插在 User 前面"。
        // 只有先处理 User (把 User 安置好)，Def 移动过来时才能找到 User 并正确插在它前面。
        // 原程序的拓扑序保证了 User 在 Def 后面，所以反向遍历就是 "先 User 后 Def"。
        Collections.reverse(allInsts);

        for (Instruction inst : allInsts) {
            if (isPinned(inst)) continue;
            if (inst.getParent() == null) continue;

            BasicBlock early = earlyBlockMap.get(inst);
            BasicBlock late = lateBlockMap.get(inst);

            if (early == null || late == null) continue;

            BasicBlock best = findBestBlock(early, late);

            if (best != inst.getParent()) {
                moveInst(inst, best);
            }
        }
    }

    /**
     * 在 [Early, Late] 支配链上，找到循环深度最小的块
     */
    private static BasicBlock findBestBlock(BasicBlock early, BasicBlock late) {
        // 如果还没有循环深度信息，假设都是 0
        int earlyDepth = early.getLoopDepth(); // 确保 BasicBlock 有这个方法，默认返回 0
        int lateDepth = late.getLoopDepth();

        // 【核心策略修正】
        // 只有当 Early 确实比 Late 处于更外层循环时，才进行提升 (LICM)
        if (earlyDepth < lateDepth) {
            return early;
        }

        // 其他情况（同层循环，或者都在循环外），优先放在 Late
        // 这样可以缩短变量的活跃周期，极大减轻寄存器分配压力
        return late;
    }

    /**
     * 执行物理移动
     */
    /**
     * 执行物理移动 (修复版)
     * 策略：如果目标块中有指令使用了 inst，必须插在那个使用者之前。
     * 否则，插在块末尾(终结指令前)。
     */
    private static void moveInst(Instruction inst, BasicBlock targetBlock) {
        // 1. 从原位置移除
        if (inst.getParent() != null) {
            inst.getParent().getInstructions().remove(inst);
        }
        inst.setParent(targetBlock); // 更新父指针

        // 2. 寻找插入点
        // 我们需要找到 targetBlock 中 *第一个* 使用了 inst 的指令
        Instruction insertBefore = null;

        for (Instruction i : targetBlock.getInstructions()) {
            // 检查指令 i 的操作数里是否有 inst
            for (Use op : i.getOperands()) {
                Value operand = op.getValue();
                if (operand == inst) {
                    insertBefore = i;
                    break;
                }
            }
            if (insertBefore != null) break; // 找到了第一个使用者，停止搜索
        }

        // 3. 执行插入
        List<Instruction> list = targetBlock.getInstructions();

        if (insertBefore != null) {
            // 情况 A: 目标块里有使用者 (比如 putint)
            // 我们把 inst 插在使用者前面
            int index = list.indexOf(insertBefore);
            // 安全检查：虽然理论上 index 不会为 -1
            if (index != -1) {
                list.add(index, inst);
            } else {
                // 异常情况兜底，插在最后
                insertAtEnd(list, inst);
            }
        } else {
            // 情况 B: 目标块里没有使用者 (inst 的结果可能在后续块才用)
            // 插在块末尾 (终结指令之前)
            insertAtEnd(list, inst);
        }
    }

    // 辅助方法：插在块末尾
    private static void insertAtEnd(List<Instruction> list, Instruction inst) {
        // 修改点：使用 list.get(list.size() - 1) 代替 list.getLast()
        if (!list.isEmpty() && list.get(list.size() - 1) instanceof TerminatorInst) {
            // 如果最后一条是终结指令 (br/ret)，插在它前面
            list.add(list.size() - 1, inst);
        } else {
            // 否则直接追加到最后
            list.add(inst);
        }
    }
}
