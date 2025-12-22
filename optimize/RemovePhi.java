package optimize;

import backend.enums.Register;
import middle.component.inst.BrInst;
import middle.component.inst.Instruction;
import middle.component.inst.MoveInst;
import middle.component.inst.PhiInst;
import middle.component.model.BasicBlock;
import middle.component.model.Function;
import middle.component.model.Module;
import middle.component.model.Value;
import middle.component.type.IntegerType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class RemovePhi {
    private static Map<Value, Register> var2reg;
    private static int tempCounter = 0;

    public static void run(Module module) {
        for (Function function : module.getFunctions()) {
            if (function.isDeclaration()) continue;

            // 获取寄存器分配信息 (如果在 RegAlloc 后运行)
            var2reg = function.getVar2reg();
            if (var2reg == null) var2reg = new HashMap<>();

            // 复制 Block 列表以防遍历时修改
            ArrayList<BasicBlock> blocks = new ArrayList<>(function.getBasicBlocks());
            for (BasicBlock b : blocks) {
                removePhi(b);
            }
        }
    }

    private static void removePhi(BasicBlock currentBlock) {
        ArrayList<Instruction> instructions = new ArrayList<>(currentBlock.getInstructions());
        HashMap<BasicBlock, ArrayList<MoveInst>> moves = new HashMap<>();

        // 初始化所有前驱的 Move 列表
        for (BasicBlock parent : currentBlock.getPrevBlocks()) {
            moves.put(parent, new ArrayList<>());
        }

        for (Instruction instruction : instructions) {
            if (!(instruction instanceof PhiInst)) {
                break; // Phi 指令一定在块开头
            }
            PhiInst phiInst = (PhiInst) instruction;

            // 【关键修复 1】：手动且安全地解析操作数，防止 IndexOutOfBounds
            // 不要调用 phiInst.getIncomingValues()，因为它的内部逻辑可能在优化过程中失效
            ArrayList<Value> alternatives = new ArrayList<>();
            ArrayList<BasicBlock> blocks = new ArrayList<>();

            int numOps = phiInst.getNumOperands();
            for (int i = 0; i < numOps; i += 2) {
                // 防御性检查：确保操作数是成对的 (Value, Block)
                if (i + 1 >= numOps) break;

                Value val = phiInst.getOperand(i);
                Value blkVal = phiInst.getOperand(i + 1);

                if (blkVal instanceof BasicBlock) {
                    alternatives.add(val);
                    blocks.add((BasicBlock) blkVal);
                }
            }

            // 过滤无效前驱 (Sanity Check)
            for (int i = 0; i < blocks.size(); i++) {
                if (!currentBlock.getPrevBlocks().contains(blocks.get(i))) {
                    blocks.remove(i);
                    alternatives.remove(i);
                    i--;
                }
            }

            // 生成 Move 意图
            for (int i = 0; i < alternatives.size(); i++) {
                Value val = alternatives.get(i);
                BasicBlock srcBlock = blocks.get(i);
                if (moves.containsKey(srcBlock)) {
                    moves.get(srcBlock).add(new MoveInst(phiInst, val));
                }
            }

            // 从当前块移除 Phi 指令
            currentBlock.getInstructions().remove(instruction);
            instruction.setParent(null);
        }

        // 2. 处理 Move 插入
        ArrayList<BasicBlock> parents = new ArrayList<>(currentBlock.getPrevBlocks());

        for (BasicBlock parent : parents) {
            if (moves.get(parent) == null || moves.get(parent).isEmpty()) {
                continue;
            }

            ArrayList<MoveInst> moveList = moves.get(parent);
            ArrayList<MoveInst> parallels = new ArrayList<>();

            // ---------------------------------------------------------
            // 解决并行赋值冲突 - 值冲突
            // ---------------------------------------------------------
            for (int i = 0; i < moveList.size(); i++) {
                for (int j = i + 1; j < moveList.size(); j++) {
                    if (moveList.get(i).getToValue().equals(moveList.get(j).getFromValue())) {
                        // 【关键修复 2】：变量名必须以 % 开头
                        String name = "%pc_temp_" + (tempCounter++);
                        Value tempValue = new Value(moveList.get(i).getToValue().getType());
                        tempValue.setName(name);

                        MoveInst tempMove = new MoveInst(tempValue, moveList.get(i).getToValue());
                        parallels.add(0, tempMove);

                        for (int k = j; k < moveList.size(); k++) {
                            if (moveList.get(i).getToValue().equals(moveList.get(k).getFromValue())) {
                                moveList.get(k).setOperand(1, tempValue);
                            }
                        }
                    }
                }
                parallels.add(moveList.get(i));
            }

            // ---------------------------------------------------------
            // 解决物理寄存器冲突 (RegAlloc 后)
            // ---------------------------------------------------------
            ArrayList<MoveInst> finalMoves = new ArrayList<>();
            for (int i = 0; i < parallels.size(); i++) {
                for (int j = i + 1; j < parallels.size(); j++) {
                    Value destI = parallels.get(i).getToValue();
                    Value srcJ = parallels.get(j).getFromValue();

                    if (var2reg.containsKey(destI) && var2reg.containsKey(srcJ) &&
                            var2reg.get(destI).equals(var2reg.get(srcJ))) {

                        // 【关键修复 2】：变量名必须以 % 开头
                        String name = "%pc_reg_temp_" + (tempCounter++);
                        Value tempValue = new Value(moveList.get(i).getToValue().getType());
                        tempValue.setName(name);

                        MoveInst tempMove = new MoveInst(tempValue, destI);
                        finalMoves.add(0, tempMove);

                        for (int k = j; k < parallels.size(); k++) {
                            Value srcK = parallels.get(k).getFromValue();
                            if (var2reg.containsKey(srcK) &&
                                    var2reg.get(destI).equals(var2reg.get(srcK))) {
                                parallels.get(k).setOperand(1, tempValue);
                            }
                        }
                    }
                }
                finalMoves.add(parallels.get(i));
            }

            // ---------------------------------------------------------
            // 关键边拆分 (Critical Edge Splitting) 与指令插入
            // ---------------------------------------------------------
            if (parent.getNextBlocks().size() > 1) {
                // 【关键修复 3】：传入 null 防止自动添加到函数末尾导致重复
                BasicBlock newBlock = new BasicBlock("pc_edge_" + (tempCounter++), null);
                newBlock.setParent(currentBlock.getParent());

                LinkedList<BasicBlock> funcBlocks = currentBlock.getParent().getBasicBlocks();
                int idx = funcBlocks.indexOf(currentBlock);

                if (idx != -1) funcBlocks.add(idx, newBlock);
                else funcBlocks.add(newBlock);

                for (Instruction inst : finalMoves) {
                    newBlock.addInstruction(inst);
                }

                BrInst brToCurrent = new BrInst(currentBlock);
                newBlock.addInstruction(brToCurrent);

                Instruction terminator = parent.getLastInstruction();
                if (terminator instanceof BrInst) {
                    BrInst parentBr = (BrInst) terminator;
                    for (int k = 0; k < parentBr.getNumOperands(); k++) {
                        if (parentBr.getOperand(k) == currentBlock) {
                            parentBr.setOperand(k, newBlock);
                        }
                    }
                }

                parent.getNextBlocks().remove(currentBlock);
                parent.getNextBlocks().add(newBlock);
                newBlock.getPrevBlocks().add(parent);
                newBlock.getNextBlocks().add(currentBlock);
                currentBlock.getPrevBlocks().remove(parent);
                currentBlock.getPrevBlocks().add(newBlock);

            } else {
                Instruction terminator = parent.getLastInstruction();
                int insertIdx = parent.getInstructions().indexOf(terminator);
                if (insertIdx == -1) insertIdx = parent.getInstructions().size();

                for (Instruction inst : finalMoves) {
                    parent.getInstructions().add(insertIdx++, inst);
                    inst.setParent(parent);
                }
            }
        }
    }
}