package backend.utils;

import backend.enums.Register;
import middle.component.inst.CallInst;
import middle.component.inst.Instruction;
import middle.component.inst.PhiInst;
import middle.component.inst.ZextInst;
import middle.component.model.*;
import middle.component.model.Module;
import optimize.Mem2Reg;

import java.util.*;

/**
 * 图着色寄存器分配器 (Refactored)
 * 实现了基于 Chaitin-Briggs 算法的寄存器分配
 */
public class RegAlloc {
    // 活跃区间分析结果
    private Map<BasicBlock, Set<Value>> liveIn;  // IN 集合
    private Map<BasicBlock, Set<Value>> liveOut; // OUT 集合
    private Map<BasicBlock, Set<Value>> defs;    // Use 集合
    private Map<BasicBlock, Set<Value>> uses;    // Def 集合

    // 干涉图结构
    private Map<Value, Node> nodeCache; // 变量 -> 节点
    private Set<Node> graphNodes;       // 所有节点

    // 配置参数
    private int kColors; // 可用颜色数量 (K)
    private final boolean enableExtraRegs = false; // 对应原本的 aggressive

    /**
     * 执行分配的主入口
     */
    public void run(Module module) {
        // 1. 预处理：SSA 转换
        Mem2Reg.run(module, false);

        // 2. 初始化物理寄存器池
        List<Register> phyRegs = new ArrayList<>();
        for (Register r : Register.values()) {
            // 只使用 t0-t9 作为通用分配寄存器
            if (r.ordinal() >= Register.T0.ordinal() && r.ordinal() <= Register.T7.ordinal()) {
                phyRegs.add(r);
            }
        }
        // 如果开启激进模式，额外使用 gp, fp
        if (enableExtraRegs) {
            phyRegs.add(Register.GP);
            phyRegs.add(Register.FP);
        }
        this.kColors = phyRegs.size();

        // 3. 对每个函数独立进行分配
        for (Function func : module.getFunctions()) {
            // A. 数据流分析
            initAnalysis(func);
            performLivenessAnalysis(func);

            // B. 构建干涉图
            buildGraph(func);

            // C. 图着色
            assignColors();

            // D. 结果回写 (Mapping)
            HashMap<Value, Register> allocationResult = new HashMap<>();
            for (Node n : nodeCache.values()) {
                // 跳过溢出的节点
                if (n.spilled) continue;

                // 将颜色映射回物理寄存器
                Register reg = phyRegs.get(n.colorIndex);
                allocationResult.put(n.val, reg);
                // System.out.println("[Alloc] " + n.val.getName() + " -> " + reg);
            }

            // E. 处理函数调用的活跃寄存器 (Caller-Saved 优化)
            resolveCallSites(func, allocationResult);

            // F. 保存结果到 Function 对象，供后端使用
            func.setVar2reg(allocationResult);
        }
    }

    /**
     * 处理 Call指令的寄存器保存集合
     * 核心逻辑：计算在该 Call 指令处，有哪些已分配寄存器的变量是活跃的
     */
    private void resolveCallSites(Function func, Map<Value, Register> mapping) {
        for (BasicBlock bb : func.getBasicBlocks()) {
            List<Instruction> insts = bb.getInstructions();
            for (int i = 0; i < insts.size(); i++) {
                Instruction inst = insts.get(i);

                // 只关注 Call 指令
                if (!(inst instanceof CallInst)) continue;

                CallInst call = (CallInst) inst;
                Set<Register> activeRegs = new HashSet<>();

                // 1. 检查块出口活跃的变量 (OUT集合)
                for (Value v : liveOut.get(bb)) {
                    if (mapping.containsKey(v)) {
                        activeRegs.add(mapping.get(v));
                    }
                }

                // 2. 检查 Call 指令之后被使用的变量 (在同一个块内)
                // 逻辑：如果变量在 Call 后面被用到，说明它跨越了 Call，必须保存
                for (int j = i + 1; j < insts.size(); j++) {
                    for (int k = 0;k < insts.get(j).getNumOperands();k++) {
                        Value op = insts.get(j).getOperand(k);
                        if (mapping.containsKey(op)) {
                            activeRegs.add(mapping.get(op));
                        }
                    }
                }

                // 设置到指令中，供 MipsBuilder 使用
                call.setActiveReg(activeRegs);
            }
        }
    }

    // --- 数据流分析阶段 ---

    private void initAnalysis(Function f) {
        liveIn = new HashMap<>();
        liveOut = new HashMap<>();
        defs = new HashMap<>();
        uses = new HashMap<>();

        for (BasicBlock bb : f.getBasicBlocks()) {
            liveIn.put(bb, new HashSet<>());
            liveOut.put(bb, new HashSet<>());
            defs.put(bb, new HashSet<>());
            uses.put(bb, new HashSet<>());
            analyzeBlockDefUse(bb);
        }
    }

    private void analyzeBlockDefUse(BasicBlock bb) {
        Set<Value> defList = defs.get(bb);
        Set<Value> useList = uses.get(bb);

        // 1. 处理 Phi 节点
        for (Instruction inst : bb.getInstructions()) {
            if (inst instanceof PhiInst) {
                for (int k = 0;k < inst.getNumOperands();k++) {
                    Value op = inst.getOperand(k);
                    if (canAllocate(op) && !defList.contains(op)) {
                        useList.add(op);
                    }
                }
            }
        }

        // 2. 处理普通指令
        for (Instruction inst : bb.getInstructions()) {
            // 记录 Use
            for (int k = 0;k < inst.getNumOperands();k++) {
                Value op = inst.getOperand(k);
                if (canAllocate(op) && !defList.contains(op)) {
                    useList.add(op);
                }
            }
            // 记录 Def (排除 Zext 和 无名指令)
            if (!inst.getName().isEmpty() && !(inst instanceof ZextInst)) {
                defList.add(inst);
            }
        }
    }

    private void performLivenessAnalysis(Function f) {
        List<BasicBlock> blockList = f.getBasicBlocks();
        boolean isStable = false;

        // 迭代计算直到不动点
        while (!isStable) {
            isStable = true;
            // 逆序遍历有助于加速收敛
            for (int i = blockList.size() - 1; i >= 0; i--) {
                BasicBlock bb = blockList.get(i);

                // OUT[B] = Union(IN[S]) for S in successors
                Set<Value> newOut = new HashSet<>();
                for (BasicBlock succ : bb.getNextBlocks()) {
                    newOut.addAll(liveIn.get(succ));
                }

                // 特殊处理 Phi 指令的数据流
                for (BasicBlock succ : bb.getNextBlocks()) {
                    for (Instruction inst : succ.getInstructions()) {
                        if (inst instanceof PhiInst phi) {
                            int idx = phi.getBlocks().indexOf(bb);
                            if (idx != -1) {
                                Value v = phi.getOperand(idx);
                                if (canAllocate(v)) newOut.add(v);
                            }
                        } else {
                            break; // Phi 都在块开头
                        }
                    }
                }
                liveOut.put(bb, newOut);

                // IN[B] = USE[B] U (OUT[B] - DEF[B])
                Set<Value> newIn = new HashSet<>(newOut);
                newIn.removeAll(defs.get(bb));
                newIn.addAll(uses.get(bb));

                // 检查是否变化
                if (!newIn.equals(liveIn.get(bb))) {
                    liveIn.put(bb, newIn);
                    isStable = false;
                }
            }
        }
    }

    // --- 建图阶段 ---

    private void buildGraph(Function f) {
        graphNodes = new HashSet<>();
        nodeCache = new HashMap<>();

        for (BasicBlock bb : f.getBasicBlocks()) {
            // live 集合初始化为 OUT[B]
            Set<Value> currentLive = new HashSet<>(liveOut.get(bb));
            List<Instruction> insts = bb.getInstructions();

            // 逆序遍历指令
            for (int i = insts.size() - 1; i >= 0; i--) {
                Instruction inst = insts.get(i);

                // 如果指令定义了一个变量
                if (!inst.getName().isEmpty() && !(inst instanceof ZextInst)) {
                    // 定义点会让变量不再活跃 (对于前驱而言)
                    currentLive.remove(inst);

                    // 该变量与当前所有活跃变量干涉
                    Node defNode = getNode(inst);
                    for (Value liveVar : currentLive) {
                        Node liveNode = getNode(liveVar);
                        link(defNode, liveNode);
                    }
                }

                // 指令使用的变量变更为活跃
                for (int k = 0;k < inst.getNumOperands();k++) {
                    Value op = inst.getOperand(k);
                    if (canAllocate(op)) {
                        currentLive.add(op);
                    }
                }
            }
        }
    }

    private Node getNode(Value v) {
        return nodeCache.computeIfAbsent(v, Node::new);
    }

    private void link(Node u, Node v) {
        if (u != v) {
            // 双向连接
            if (u.adj.add(v)) u.deg++;
            if (v.adj.add(u)) v.deg++;
        }
    }

    // --- 着色阶段 ---

    private void assignColors() {
        Stack<Node> stack = new Stack<>();
        Set<Node> remaining = new HashSet<>(graphNodes); // 工作集

        // 1. 简化 (Simplify) 与 溢出 (Spill)
        while (!remaining.isEmpty()) {
            // 尝试寻找度数 < K 的节点
            Node candidate = null;
            for (Node n : remaining) {
                if (n.deg < kColors) {
                    candidate = n;
                    break;
                }
            }

            if (candidate == null) {
                // 如果没找到，则启发式选择溢出节点 (选择度数最大的)
                candidate = pickSpillCandidate(remaining);
            }

            // 从图中移除节点（入栈）
            remaining.remove(candidate);
            stack.push(candidate);

            // 更新邻居的度数
            for (Node neighbor : candidate.adj) {
                // 只有还在图中的邻居才需要减度数
                // 这里用一种隐式的方式：我们不需要真正从 neighbor.adj 删掉 candidate
                // 只需要逻辑上减少度数即可，因为 removed 的节点不会再被处理
                if (remaining.contains(neighbor)) {
                    neighbor.deg--;
                }
            }
        }

        // 2. 选择 (Select)
        while (!stack.isEmpty()) {
            Node n = stack.pop();
            Set<Integer> usedColors = new HashSet<>();

            // 查看邻居用了什么颜色
            for (Node neighbor : n.adj) {
                // 注意：这里需要判断 neighbor 是否已经着色
                // 因为在栈中弹出的顺序决定了谁先着色
                if (neighbor.colorIndex != -1) {
                    usedColors.add(neighbor.colorIndex);
                }
            }

            // 寻找最小可用颜色
            int chosenColor = -1;
            for (int c = 0; c < kColors; c++) {
                if (!usedColors.contains(c)) {
                    chosenColor = c;
                    break;
                }
            }

            if (chosenColor != -1) {
                n.colorIndex = chosenColor;
            } else {
                n.spilled = true; // 真的没颜色了，标记溢出
            }
        }
    }

    private Node pickSpillCandidate(Set<Node> nodes) {
        Node best = null;
        int maxDeg = -1;
        for (Node n : nodes) {
            if (n.deg > maxDeg) {
                maxDeg = n.deg;
                best = n;
            }
        }
        return best;
    }

    private boolean canAllocate(Value v) {
        // 过滤掉常量、全局变量、基本块引用等
        // FuncParam 默认不分配（配合后端逻辑）
        if (v instanceof ConstInt) return false;
        if (v instanceof ConstString) return false;
        if (v instanceof GlobalVar) return false;
        if (v instanceof BasicBlock) return false;
        if (v instanceof Function) return false;
        if (v instanceof FuncParam) return false;
        return true;
    }

    // --- 内部类 ---

    private class Node {
        Value val;
        Set<Node> adj = new HashSet<>(); // 邻接表
        int deg = 0;                     // 当前度数 (动态变化)
        int colorIndex = -1;             // 分配的颜色ID
        boolean spilled = false;         // 溢出标记

        Node(Value v) {
            this.val = v;
            // 将节点加入全集
            graphNodes.add(this);
        }
    }
}