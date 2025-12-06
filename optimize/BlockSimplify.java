package optimize;

import middle.component.inst.BrInst;
import middle.component.inst.Instruction;
import middle.component.inst.PhiInst;
import middle.component.model.BasicBlock;
import middle.component.model.Function;
import middle.component.model.Module;
import middle.component.model.Value;

import java.util.*;

public class BlockSimplify {

    public static void run(Module module) {
        // 1. Initial cleanup
        Mem2Reg.run(module, true);

        boolean changed;
        int iteration = 0;
        final int MAX_ITERATIONS = 10;

        do {
            changed = false;
            Mem2Reg.run(module, true);

            for (Function function : module.getFunctions()) {
                if (function.isDeclaration()) continue;

                // 3. DFS reorder
                List<BasicBlock> orderedBlocks = reorderBasicBlocksDFS(function);
                if (!orderedBlocks.equals(function.getBasicBlocks())) {
                    function.getBasicBlocks().clear();
                    function.getBasicBlocks().addAll(orderedBlocks);
                    changed = true;
                }

                // 4. Dead block elimination
                if (removeUnreachableBlocks(function)) {
                    changed = true;
                }

                // 5. Block merging
                if (mergeBlocks(function)) {
                    changed = true;
                }
            }
            iteration++;
        } while (changed && iteration < MAX_ITERATIONS);

        Mem2Reg.run(module, true);
    }

    private static List<BasicBlock> reorderBasicBlocksDFS(Function function) {
        List<BasicBlock> ordered = new ArrayList<>();
        Set<BasicBlock> visited = new HashSet<>();
        Deque<BasicBlock> stack = new ArrayDeque<>();

        BasicBlock entry = function.getEntryBlock();
        if (entry != null) {
            stack.push(entry);
        }

        while (!stack.isEmpty()) {
            BasicBlock current = stack.pop();
            if (!visited.contains(current)) {
                visited.add(current);
                ordered.add(current);

                List<BasicBlock> successors = getSuccessors(current);
                Collections.reverse(successors);

                for (BasicBlock succ : successors) {
                    if (!visited.contains(succ)) {
                        stack.push(succ);
                    }
                }
            }
        }

        // Append unreachable blocks to be safe (they will be removed by removeUnreachableBlocks)
        for (BasicBlock bb : function.getBasicBlocks()) {
            if (!visited.contains(bb)) {
                ordered.add(bb);
            }
        }
        return ordered;
    }

    private static List<BasicBlock> getSuccessors(BasicBlock block) {
        Instruction term = block.getLastInstruction();
        List<BasicBlock> succs = new ArrayList<>();

        if (term instanceof BrInst) {
            BrInst br = (BrInst) term;
            succs.add((BasicBlock) br.getTrueDest());
            if (br.isConditional()) {
                succs.add((BasicBlock) br.getFalseDest());
            }
        }
        return succs;
    }

    private static boolean removeUnreachableBlocks(Function func) {
        Set<BasicBlock> reachable = new HashSet<>();
        Queue<BasicBlock> workList = new LinkedList<>();
        BasicBlock entry = func.getEntryBlock();

        if (entry != null) {
            reachable.add(entry);
            workList.add(entry);
        }

        while (!workList.isEmpty()) {
            BasicBlock bb = workList.poll();
            for (BasicBlock succ : getSuccessors(bb)) {
                if (!reachable.contains(succ)) {
                    reachable.add(succ);
                    workList.add(succ);
                }
            }
        }

        List<BasicBlock> toRemove = new ArrayList<>();
        for (BasicBlock bb : func.getBasicBlocks()) {
            if (!reachable.contains(bb)) {
                toRemove.add(bb);
            }
        }

        if (toRemove.isEmpty()) return false;

        for (BasicBlock dead : toRemove) {
            for (BasicBlock succ : getSuccessors(dead)) {
                succ.getPrevBlocks().remove(dead);
                for (Instruction inst : succ.getInstructions()) {
                    if (inst instanceof PhiInst) {
                        PhiInst phi = (PhiInst) inst;
                        for (int i = phi.getNumIncoming() - 1; i >= 0; i--) {
                            if (phi.getIncomingBlock(i) == dead) {
                                // [FIX]: Call removeIncoming with logical index 'i'
                                phi.removeIncoming(i);
                            }
                        }
                    }
                }
            }
            for (Instruction inst : dead.getInstructions()) {
                inst.removeOperands();
            }
            func.getBasicBlocks().remove(dead);
        }
        return true;
    }

    private static boolean mergeBlocks(Function function) {
        boolean merged = false;
        List<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());

        for (BasicBlock block : blocks) {
            if (!function.getBasicBlocks().contains(block)) continue;

            List<BasicBlock> succs = getSuccessors(block);
            if (succs.size() != 1) continue;

            BasicBlock child = succs.get(0);

            if (child.getPrevBlocks().size() != 1 || child.getPrevBlocks().get(0) != block) continue;
            if (child == function.getEntryBlock()) continue;

            performMerge(block, child);
            merged = true;
        }
        return merged;
    }

    private static void performMerge(BasicBlock parent, BasicBlock child) {
        Instruction term = parent.getLastInstruction();
        term.removeOperands();
        parent.getInstructions().remove(term);

        Iterator<Instruction> it = child.getInstructions().iterator();
        while (it.hasNext()) {
            Instruction inst = it.next();
            it.remove();

            if (inst instanceof PhiInst) {
                PhiInst phi = (PhiInst) inst;
                Value val = phi.getIncomingValue(0);
                phi.replaceAllUsesWith(val);
                phi.removeOperands();
            } else {
                parent.addInstruction(inst);
            }
        }

        for (BasicBlock succ : getSuccessors(parent)) {
            succ.getPrevBlocks().remove(child);
            succ.getPrevBlocks().add(parent);

            for (Instruction i : succ.getInstructions()) {
                if (i instanceof PhiInst) {
                    PhiInst phi = (PhiInst) i;
                    for (int k = 0; k < phi.getNumIncoming(); k++) {
                        if (phi.getIncomingBlock(k) == child) {
                            // [NOTE]: Here we manipulate operands directly because we are setting, not removing
                            // This assumes User has setOperand(index, value)
                            // Block is at odd index (2*k + 1)
                            phi.setOperand(2 * k + 1, parent);
                        }
                    }
                }
            }
        }

        parent.getParent().getBasicBlocks().remove(child);
        child.replaceAllUsesWith(parent);
    }
}
