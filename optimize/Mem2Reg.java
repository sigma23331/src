package optimize;

import middle.component.inst.*;
import middle.component.model.*;
import middle.component.model.Module;
import middle.component.type.IntegerType;
import middle.component.type.PointerType;
import middle.component.type.Type;
import middle.component.type.UnDefined;

import java.util.*;

/**
 * 内存提升优化 (Mem2Reg) - Fixed
 */
public class Mem2Reg {

    public static void run(Module module, boolean enabled) {
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue; // 跳过声明
            new FunctionContext(func).execute(enabled);
        }
    }

    private static class FunctionContext {
        private final Function function;

        // CFG & DomTree Maps
        private final Map<BasicBlock, List<BasicBlock>> cfgSuccessors = new HashMap<>();
        private final Map<BasicBlock, List<BasicBlock>> cfgPredecessors = new HashMap<>();
        private final Map<BasicBlock, List<BasicBlock>> domTreeChildren = new HashMap<>();
        private final Map<BasicBlock, List<BasicBlock>> dominators = new HashMap<>();
        private final Map<BasicBlock, List<BasicBlock>> domBy = new HashMap<>();
        // private final Map<BasicBlock, BasicBlock> idomMap = new HashMap<>(); // 暂未使用，可注释

        // Mem2Reg State
        private AllocInst activeAlloca;
        private final List<BasicBlock> defBlockList = new ArrayList<>();
        private final Set<Instruction> defInstSet = new HashSet<>();
        private final Set<Instruction> useInstSet = new HashSet<>();
        private final Stack<Value> versionStack = new Stack<>();

        public FunctionContext(Function function) {
            this.function = function;
        }

        public void execute(boolean performPromotion) {
            buildCFG();
            buildDominatorTree();
            computeDominanceFrontiers();

            if (!performPromotion) return;

            promoteMemoryToRegister();
        }

        // --- Phase 1: CFG ---
        private void buildCFG() {
            for (BasicBlock bb : function.getBasicBlocks()) {
                cfgSuccessors.put(bb, new ArrayList<>());
                cfgPredecessors.put(bb, new ArrayList<>());
                dominators.put(bb, new ArrayList<>());
                domBy.put(bb, new ArrayList<>());
                domTreeChildren.put(bb, new ArrayList<>());
            }

            for (BasicBlock bb : function.getBasicBlocks()) {
                Instruction term = bb.getTerminator();
                if (term instanceof BrInst br) {
                    if (br.isConditional()) {
                        linkBlocks(bb, (BasicBlock) br.getTrueDest());
                        linkBlocks(bb, (BasicBlock) br.getFalseDest());
                    } else {
                        linkBlocks(bb, (BasicBlock) br.getTrueDest());
                    }
                }
            }

            // 更新 BasicBlock 内部引用
            for (BasicBlock bb : function.getBasicBlocks()) {
                bb.setNextBlocks(cfgSuccessors.get(bb));
                bb.setPrevBlocks(cfgPredecessors.get(bb));
            }
        }

        private void linkBlocks(BasicBlock pred, BasicBlock succ) {
            if (!cfgSuccessors.get(pred).contains(succ)) {
                cfgSuccessors.get(pred).add(succ);
            }
            if (!cfgPredecessors.get(succ).contains(pred)) {
                cfgPredecessors.get(succ).add(pred);
            }
        }

        // --- Phase 2: Dominator Tree ---
        private void buildDominatorTree() {
            BasicBlock entryBlock = function.getEntryBlock();
            List<BasicBlock> allBlocks = function.getBasicBlocks();

            // 2.1 Calculate Dominators (O(N^2))
            for (BasicBlock domCandidate : allBlocks) {
                Set<BasicBlock> reachable = new HashSet<>();
                // 查找如果不经过 domCandidate，entry 能到达哪些点
                findReachable(entryBlock, domCandidate, reachable);

                for (BasicBlock bb : allBlocks) {
                    // 如果移除 candidate 后 bb 不可达，则 candidate 支配 bb
                    // 注意：这里需要处理 unreachable blocks (永远不可达的块)
                    if (!reachable.contains(bb)) {
                        dominators.get(domCandidate).add(bb);
                        domBy.get(bb).add(domCandidate);
                    }
                }
                domCandidate.setDominateBlocks(dominators.get(domCandidate));
            }

            // 2.2 Calculate IDom
            for (BasicBlock bb : allBlocks) {
                // 如果块本身不可达，跳过
                if (domBy.get(bb).isEmpty()) continue;

                for (BasicBlock dom : domBy.get(bb)) {
                    if (isStrictIDom(dom, bb)) {
                        // idomMap.put(bb, dom);
                        bb.setImmediateDominator(dom);
                        domTreeChildren.get(dom).add(bb);
                        break;
                    }
                }
            }

            // 更新 Block 引用
            for (BasicBlock bb : allBlocks) {
                bb.setImmediateDominateBlocks(domTreeChildren.get(bb));
            }
        }

        private void findReachable(BasicBlock current, BasicBlock forbidden, Set<BasicBlock> visited) {
            if (current == forbidden || visited.contains(current)) return;
            visited.add(current);

            for (BasicBlock succ : cfgSuccessors.get(current)) {
                findReachable(succ, forbidden, visited);
            }
        }

        private boolean isStrictIDom(BasicBlock dom, BasicBlock bb) {
            if (dom == bb) return false; // IDom 必须是严格支配
            // 检查 dom 是否是“最近”的支配者
            // 即：不存在另一个节点 other，使得 dom 支配 other，且 other 支配 bb
            for (BasicBlock other : domBy.get(bb)) {
                if (other != dom && other != bb) {
                    if (dominators.get(dom).contains(other)) {
                        return false;
                    }
                }
            }
            return true;
        }

        // --- Phase 3: DF ---
        private void computeDominanceFrontiers() {
            for (BasicBlock runner : function.getBasicBlocks()) {
                List<BasicBlock> frontiers = new ArrayList<>();
                for (BasicBlock domChild : runner.getDominateBlocks()) {
                    for (BasicBlock succ : cfgSuccessors.get(domChild)) {
                        if (!runner.getDominateBlocks().contains(succ) || succ == runner) {
                            if (!frontiers.contains(succ)) {
                                frontiers.add(succ);
                            }
                        }
                    }
                }
                runner.setDominanceFrontier(frontiers);
            }
        }

        // --- Phase 4: Promote ---
        private void promoteMemoryToRegister() {
            for (BasicBlock bb : function.getBasicBlocks()) {
                // 使用副本遍历，防止 ConcurrentModificationException
                List<Instruction> insts = new ArrayList<>(bb.getInstructions());
                for (Instruction inst : insts) {
                    if (inst instanceof AllocInst alloc) {
                        // 检查是否是基本类型 (i32/i8)
                        // 注意：AllocInst 的 AllocatedType 通常是元素类型 (如 i32)
                        // 如果是指针类型 (如 i32*)，则需要检查 getPointeeType
                        boolean isPromotable = false;
                        Type type = alloc.getAllocatedType(); // 假设这里返回的是分配的内容类型

                        if (type.equals(IntegerType.get32()) || type.equals(IntegerType.get8())) {
                            isPromotable = true;
                        }

                        if (isPromotable) {
                            this.activeAlloca = alloc;
                            analyzeAllocaUsage();
                            // 如果 activeAlloca 被用作除了 load/store 之外的用途（例如函数传参），
                            // 则不能提升 (escape analysis)，这里简单起见假设前端未生成取地址操作
                            placePhiNodes();
                            renameInDomTree(function.getEntryBlock());
                        }
                    }
                }
            }
        }

        private void analyzeAllocaUsage() {
            defBlockList.clear();
            defInstSet.clear();
            useInstSet.clear();
            versionStack.clear();

            for (Use use : activeAlloca.getUseList()) {
                User user = use.getUser();
                // 忽略已删除指令的引用
                if (!(user instanceof Instruction)) continue;
                Instruction inst = (Instruction) user;
                if (inst.getParent().isDeleted()) continue;

                if (inst instanceof LoadInst) {
                    useInstSet.add(inst);
                } else if (inst instanceof StoreInst store) {
                    // 【Bug Fix】必须检查 Store 的目标地址(pointer)是否是当前 alloc
                    // 只有 store val, %alloca 才是定义
                    if (store.getPointer() == activeAlloca) {
                        defInstSet.add(inst);
                        BasicBlock bb = inst.getParent();
                        if (!defBlockList.contains(bb)) {
                            defBlockList.add(bb);
                        }
                    }
                }
            }
        }

        private void placePhiNodes() {
            Set<BasicBlock> hasPhi = new HashSet<>();
            List<BasicBlock> workList = new ArrayList<>(defBlockList);

            while (!workList.isEmpty()) {
                BasicBlock defBlock = workList.remove(0);
                for (BasicBlock frontierBlock : defBlock.getDominanceFrontier()) {
                    if (!hasPhi.contains(frontierBlock)) {
                        PhiInst phi = new PhiInst(activeAlloca.getAllocatedType(), frontierBlock,
                                new ArrayList<>(frontierBlock.getPrevBlocks()));

                        phi.setParent(frontierBlock);
                        // 插入到块首
                        frontierBlock.getInstructions().add(0, phi);

                        hasPhi.add(frontierBlock);
                        useInstSet.add(phi);
                        defInstSet.add(phi);

                        if (!defBlockList.contains(frontierBlock)) {
                            workList.add(frontierBlock);
                        }
                    }
                }
            }
        }

        private void renameInDomTree(BasicBlock bb) {
            int stackSnapshot = versionStack.size();

            // A. 处理块内指令
            Iterator<Instruction> it = bb.getInstructions().iterator();
            while (it.hasNext()) {
                Instruction inst = it.next();

                if (inst == activeAlloca) {
                    it.remove();
                    inst.removeOperands(); // 彻底断开连接
                }
                else if (inst instanceof LoadInst && useInstSet.contains(inst)) {
                    Value val = versionStack.isEmpty() ? new UnDefined() : versionStack.peek();
                    inst.replaceAllUsesWith(val);
                    inst.removeOperands();
                    it.remove();
                }
                else if (inst instanceof StoreInst store && defInstSet.contains(inst)) {
                    versionStack.push(store.getValue()); // 假设 getValue() 返回要存的数据
                    inst.removeOperands();
                    it.remove();
                }
                else if (inst instanceof PhiInst && defInstSet.contains(inst)) {
                    versionStack.push(inst);
                }
            }

            // B. 填充后继块中 Phi 指令的参数
            for (BasicBlock succ : cfgSuccessors.get(bb)) {
                // 【Bug Fix】必须遍历所有开头的 Phi 指令
                for (Instruction inst : succ.getInstructions()) {
                    if (inst instanceof PhiInst phi) {
                        if (useInstSet.contains(phi)) {
                            Value val = versionStack.isEmpty() ? new UnDefined() : versionStack.peek();
                            phi.addIncoming(val, bb);
                        }
                    } else {
                        // Phi 指令一定在块的最前面，遇到非 Phi 就可以停止了
                        break;
                    }
                }
            }

            // C. 递归
            for (BasicBlock child : domTreeChildren.get(bb)) {
                renameInDomTree(child);
            }

            // D. 回溯
            while (versionStack.size() > stackSnapshot) {
                versionStack.pop();
            }
        }
    }
}