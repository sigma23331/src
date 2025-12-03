package backend.utils;

import backend.enums.AsmOp;
import backend.enums.Register;
import backend.text.*; // 假设你的汇编指令类都在这里
import middle.component.model.Function;
import middle.component.model.Module;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static backend.enums.AsmOp.*;

public class PeepHole {

    /**
     * 运行窥孔优化
     * @param insts 原始汇编指令列表 (可以是 Object 或统一的父类 AsmNode)
     * @return 优化后的汇编指令列表
     */
    public static List<Object> run(List<Object> insts) {
        // 由于优化可能会多轮迭代，我们这里简单包装一下
        // 原代码是直接修改全局单例 MipsFile，这里改为操作传入的 list

        // 1. 移除冗余跳转 (J label; label:)
        insts = removeJump(insts);

        // 2. 转换 addiu $t0, $t1, 0 -> move
        insts = transfer2Move(insts);

        // 3. 移除冗余 Move (move $t0, $t0)
        insts = removeMove(insts);

        // 4. 移除冗余访存 (sw $t0, off($sp); lw $t0, off($sp))
        insts = memPairRemoval(insts);

        // 5. 反转条件分支 (beq ... j label -> bne ... label)
        insts = reverseCondBr(insts);

        // 6. 移除不可达代码块的跳转
        insts = removeJump1(insts);

        // 7. 移除冗余的 LI/LA (如果寄存器值没变)
        insts = removeLiLa(insts);

        // 8. (可选) 移除 main 函数末尾多余的 exit syscall (如果框架不需要)
        // insts = uselessEnd(insts); 

        return insts;
    }

    // --- 优化 1: removeJump ---
    // 场景: j label; label:
    private static List<Object> removeJump(List<Object> insts) {
        List<Object> toRemove = IntStream.range(0, insts.size() - 1)
                .filter(i -> {
                    Object current = insts.get(i);
                    Object next = insts.get(i + 1);
                    return current instanceof JumpAsm jumpAsm
                            && next instanceof Label label
                            && jumpAsm.getOp() == AsmOp.J // 确保是无条件跳转
                            && jumpAsm.getTarget() != null
                            && jumpAsm.getTarget().equals(label.getName());
                })
                .mapToObj(insts::get)
                .toList();

        // 必须创建新列表以支持修改
        List<Object> newInsts = new ArrayList<>(insts);
        newInsts.removeAll(toRemove);
        return newInsts;
    }

    // --- 优化 2: transfer2Move ---
    // 场景: addiu $t0, $t1, 0 -> move $t0, $t1
    private static List<Object> transfer2Move(List<Object> insts) {
        List<Object> newInsts = new ArrayList<>(insts);
        for (int i = 0; i < newInsts.size(); i++) {
            Object obj = newInsts.get(i);
            if (obj instanceof CalcAsm calcAsm) {
                if (calcAsm.getOperation() == AsmOp.ADDIU && calcAsm.getImm() == 0) {
                    // 替换为 Move
                    // 注意：CalcAsm(op, rd, rs, rt/imm) -> MoveAsm(dst, src)
                    // ADDIU rd, rs, 0 -> Move rd, rs
                    MoveAsm moveAsm = new MoveAsm(calcAsm.getRd(), calcAsm.getRs());
                    newInsts.set(i, moveAsm);
                }
            }
        }
        return newInsts;
    }

    // --- 优化 3: removeMove ---
    // 场景: move $t0, $t0
    // 场景: move $t1, $t2; move $t1, $t3 (死代码)
    // 场景: move $t1, $t2; move $t2, $t1 (冗余)
    private static List<Object> removeMove(List<Object> insts) {
        List<Object> result = new ArrayList<>(insts);

        // 1. move $t1, $t1
        result.removeIf(obj -> obj instanceof MoveAsm move && move.getToReg() == move.getFromReg());

        // 2. 连续写入同一寄存器 (move $t1, $t2; move $t1, $t3)
        List<Object> toRemove2 = IntStream.range(0, result.size() - 1)
                .filter(i -> result.get(i) instanceof MoveAsm
                        && result.get(i + 1) instanceof MoveAsm)
                .filter(i -> {
                    MoveAsm m1 = (MoveAsm) result.get(i);
                    MoveAsm m2 = (MoveAsm) result.get(i + 1);
                    // 如果 m2 写入 m1 的目标，且 m2 没有使用 m1 的目标(防止 swap)，则 m1 无用
                    // 但原代码逻辑很简单: getDst().equals(getDst())
                    return m1.getToReg() == m2.getToReg();
                })
                .mapToObj(result::get)
                .toList();
        result.removeAll(toRemove2);

        // 3. 冗余回写 (move $t1, $t2; move $t2, $t1)
        List<Object> toRemove3 = IntStream.range(0, result.size() - 1)
                .filter(i -> result.get(i) instanceof MoveAsm
                        && result.get(i + 1) instanceof MoveAsm)
                .filter(i -> {
                    MoveAsm m1 = (MoveAsm) result.get(i);
                    MoveAsm m2 = (MoveAsm) result.get(i + 1);
                    return m1.getToReg() == m2.getFromReg() && m2.getToReg() == m1.getFromReg();
                })
                .mapToObj(i -> result.get(i + 1)) // 删除第二条
                .toList();
        result.removeAll(toRemove3);

        return result;
    }

    // --- 优化 4: memPairRemoval ---
    // 场景: sw $t0, off($sp); lw $t0, off($sp)
    private static List<Object> memPairRemoval(List<Object> insts) {
        List<Object> result = new ArrayList<>(insts);
        for (int i = 0; i < result.size() - 1; i++) {
            Object o1 = result.get(i);
            Object o2 = result.get(i + 1);
            if (o1 instanceof MemAsm m1 && o2 instanceof MemAsm m2) {
                if (m1.getOpCode() == AsmOp.SW && m2.getOpCode() == AsmOp.LW &&
                        m1.getBaseAddr() == m2.getBaseAddr() && m1.getOffsetVal() == m2.getOffsetVal()) {

                    // 替换第二条指令 (LW) 为 Move
                    MoveAsm move = new MoveAsm(m2.getTargetReg(), m1.getTargetReg());
                    result.set(i + 1, move);
                }
            }
        }
        return result;
    }

    // --- 优化 5: reverseCondBr ---
    // 场景: beq $t0, $t1, label1; j label2; label1: -> bne $t0, $t1, label2; label1:
    private static List<Object> reverseCondBr(List<Object> insts) {
        boolean changed = true;
        List<Object> result = new ArrayList<>(insts);

        while (changed) {
            changed = false;
            for (int i = 0; i < result.size() - 2; i++) {
                Object o1 = result.get(i);
                if (!(o1 instanceof BrAsm br)) continue;

                Object o2 = result.get(i + 1);
                if (!(o2 instanceof JumpAsm jump) || jump.getOp() != AsmOp.J) continue; // 必须是无条件跳转

                Object o3 = result.get(i + 2);
                if (!(o3 instanceof Label label)) continue;

                // 模式匹配:
                // br labelA
                // j labelB
                // labelA:
                if (label.getName().equals(br.getTargetLabel()) && !br.getTargetLabel().equals(jump.getTarget())) {
                    // 反转条件
                    AsmOp newOp = switch (br.getOp()) {
                        case BEQ -> BNE;
                        case BNE -> BEQ;
                        case BLE -> AsmOp.BGT;
                        case BGE -> AsmOp.BLT;
                        case BLT -> AsmOp.BGE;
                        case BGT -> BLE;
                        default -> null;
                    };
                    if (newOp == null) continue;

                    // 修改 Br 指令的目标为 labelB (jump 的目标)
                    BrAsm newBr;
                    // 检查是寄存器比较还是立即数比较
                    if (br.getRt() == null) {
                        newBr = new BrAsm(jump.getTarget(), br.getRs(), newOp, br.getImm());
                    } else {
                        newBr = new BrAsm(jump.getTarget(), br.getRs(), newOp, br.getRt());
                    }

                    result.set(i, newBr);
                    result.remove(i + 1); // 删除 jump
                    changed = true;
                    // 注意：i 不回退，因为删除了 i+1，下一轮 i+1 变成原来的 i+2
                }
            }
        }
        return result;
    }

    // --- 优化 6: removeJump1 ---
    // 场景: j label; ... (中间无 label) ... label:
    // 说明中间的代码不可达，可以删除跳转? 不，原代码逻辑是删除 "无用的 Jump"。
    // 逻辑：如果 jump 的目标就在下一行（或隔着注释），则 jump 是多余的。
    private static List<Object> removeJump1(List<Object> insts) {
        boolean changed = true;
        List<Object> result = new ArrayList<>(insts);

        while (changed) {
            changed = false;
            for (int i = 0; i < result.size(); i++) {
                if (result.get(i) instanceof JumpAsm jump && jump.getOp() == AsmOp.J) {
                    boolean foundLabel = false;
                    int j;
                    for (j = i + 1; j < result.size(); j++) {
                        Object next = result.get(j);
                        if (next instanceof Label label && label.getName().equals(jump.getTarget())) {
                            foundLabel = true;
                            break;
                        }
                        // 如果遇到非 Label 非注释的指令，说明 jump 不是跳到紧邻的下方
                        if (!(next instanceof Label)) { // 假设没有 Comment 类，或者忽略
                            break;
                        }
                    }
                    // 如果找到了紧邻的目标 Label (中间只有其他 Label)，则 Jump 是多余的
                    if (foundLabel) {
                        result.remove(i);
                        changed = true;
                        break;
                    }
                }
            }
        }
        return result;
    }

    // --- 优化 7: removeLiLa ---
    // 场景: 全局数据流分析（简化版），如果寄存器里的值没变，就不用重新 LI/LA
    private static List<Object> removeLiLa(List<Object> insts) {
        List<Object> toRemove = new ArrayList<>();
        HashMap<Register, Integer> liUses = new HashMap<>();
        HashMap<Register, String> laUses = new HashMap<>();

        for (Object obj : insts) {
            // 遇到 Label，控制流汇聚，必须清空状态
            if (obj instanceof Label) {
                liUses.clear();
                laUses.clear();
                continue;
            }
            if (obj instanceof JumpAsm jump && jump.getOp() == AsmOp.JAL) {
                // 函数调用会破坏临时寄存器 (T0-T9)，但这里简单起见全清空
                liUses.clear();
                laUses.clear();
                continue;
            }

            Register targetReg = null;

            // 1. 分析 La
            if (obj instanceof LaAsm la) {
                targetReg = la.getDstReg();
                if (laUses.getOrDefault(targetReg, "").equals(la.getLabelName())) {
                    toRemove.add(obj); // 冗余
                } else {
                    laUses.put(targetReg, la.getLabelName());
                    liUses.remove(targetReg); // 覆盖了 li 的值
                }
                continue; // 处理完当前指令，直接下一轮
            }

            // 2. 分析 Li
            if (obj instanceof LiAsm li) {
                targetReg = li.getTarget();
                if (liUses.containsKey(targetReg) && liUses.get(targetReg).equals(li.getImm())) {
                    toRemove.add(obj);
                } else {
                    liUses.put(targetReg, li.getImm());
                    laUses.remove(targetReg);
                }
                continue;
            }

            // 3. 分析其他指令 (它们会修改寄存器，从而破坏 Li/La 的状态)
            if (obj instanceof CalcAsm calc) targetReg = calc.getRd();
            else if (obj instanceof CmpAsm cmp) targetReg = cmp.getDestReg();
            else if (obj instanceof MemAsm mem && mem.getOpCode() == AsmOp.LW) targetReg = mem.getTargetReg();
            else if (obj instanceof MoveAsm move) targetReg = move.getToReg();
            else if (obj instanceof MDRegAsm md && (md.getMdOp() == AsmOp.MFHI || md.getMdOp() == AsmOp.MFLO)) targetReg = md.getReg();
            else if (obj instanceof SyscallAsm) {
                // Syscall 可能会修改 v0 (返回值)
                targetReg = Register.V0;
                // 同时也需要检查 v0 是否用于 syscall 编号
                // (原代码有复杂的检查，这里简化为：syscall 会破坏 v0)
            }

            // 如果该指令修改了某个寄存器，从状态表中移除该寄存器
            if (targetReg != null) {
                liUses.remove(targetReg);
                laUses.remove(targetReg);
            }
        }

        List<Object> result = new ArrayList<>(insts);
        result.removeAll(toRemove);
        return result;
    }
}