package optimize;

import middle.component.inst.BinaryInst;
import middle.component.inst.BinaryOpCode;
import middle.component.inst.GepInst;
import middle.component.inst.Instruction;
import middle.component.model.BasicBlock;
import middle.component.model.ConstInt;
import middle.component.model.Function;
import middle.component.model.Value;
import middle.component.type.IntegerType;
import middle.component.model.Module;

import java.util.*;

public class GVN {

    // 记录 Hash -> 指令 的映射
    private static final Map<String, Value> valueNumberMap = new HashMap<>();

    public static void run(Module module) {
        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;
            valueNumberMap.clear();
            runOnBlock(func.getEntryBlock());
        }
        // 建议：GVN 之后通常会产生大量死代码，建议在此处显式调用 DCE
        // DeadCodeElimination.run(module);
    }

    private static void runOnBlock(BasicBlock block) {
        Set<String> currentScopeHashes = new HashSet<>();
        List<Instruction> instructions = new ArrayList<>(block.getInstructions());

        for (Instruction inst : instructions) {
            if (inst.getParent() == null) continue;

            // --- 1. 尝试常量折叠 & 代数化简 ---
            Value simplifiedVal = trySimplify(inst);

            if (simplifiedVal != null) {
                // 【关键策略】将常量“实体化”为指令
                if (simplifiedVal instanceof ConstInt c) {
                    simplifiedVal = materializeConstant(c, inst);
                }

                // 只有实体化成功才替换
                if (simplifiedVal != null) {
                    inst.replaceAllUsesWith(simplifiedVal);
                    inst.removeOperands();
                    if (inst.getParent() != null) {
                        inst.getParent().getInstructions().remove(inst);
                    }
                    continue; // 指令已优化，跳过 Hash 步骤
                }
            }

            // --- 2. GVN 哈希查表去重 ---
            String hash = getHash(inst);
            if (hash != null) {
                if (valueNumberMap.containsKey(hash)) {
                    Value leader = valueNumberMap.get(hash);
                    // 类型双重检查
                    if (leader.getType().toString().equals(inst.getType().toString())) {
                        inst.replaceAllUsesWith(leader);
                        inst.removeOperands();
                        if (inst.getParent() != null) {
                            inst.getParent().getInstructions().remove(inst);
                        }
                    }
                } else {
                    valueNumberMap.put(hash, inst);
                    currentScopeHashes.add(hash);
                }
            }
        }

        // 支配树递归
        for (BasicBlock child : block.getImmediateDominateBlocks()) {
            runOnBlock(child);
        }

        // 回溯清理
        for (String hash : currentScopeHashes) {
            valueNumberMap.remove(hash);
        }
    }

    /**
     * 【最终版】常量实体化
     * i32 常量 -> add i32 0, imm
     * i1  常量 -> icmp eq i32 0, 0 (true) / icmp ne i32 0, 0 (false)
     */
    private static Value materializeConstant(ConstInt c, Instruction insertBefore) {
        if (!(c.getType() instanceof IntegerType type)) return null;

        // 准备插入位置
        BasicBlock block = insertBefore.getParent();
        List<Instruction> list = block.getInstructions();
        int idx = list.indexOf(insertBefore);
        if (idx == -1) return null;

        BinaryInst newInst;

        // --- 情况 A: i1 布尔类型 (True/False) ---
        if (type.getBitWidth() == 1) {
            // 我们利用 i32 的 0 来生成 i1 结果
            ConstInt zero32 = ConstInt.get(IntegerType.get32(), 0);

            if (c.getValue() != 0) {
                // 目标是 true: 生成 0 == 0
                newInst = new BinaryInst(BinaryOpCode.EQ, zero32, zero32);
                newInst.setName("gvn_true");
            } else {
                // 目标是 false: 生成 0 != 0
                newInst = new BinaryInst(BinaryOpCode.NE, zero32, zero32);
                newInst.setName("gvn_false");
            }
        }
        // --- 情况 B: i32 整数类型 ---
        else {
            ConstInt zero = ConstInt.get(type, 0);
            // 生成 add 0, imm
            newInst = new BinaryInst(BinaryOpCode.ADD, zero, c);
            newInst.setName("gvn_c" + c.getValue());
        }

        // 插入并返回
        list.add(idx, newInst);
        newInst.setParent(block);
        return newInst;
    }

    // --- 综合化简逻辑 ---
    private static Value trySimplify(Instruction inst) {
        if (inst instanceof BinaryInst binary) {
            Value lhs = binary.getOperand(0);
            Value rhs = binary.getOperand(1);

            // 1. 双常量折叠
            if (lhs instanceof ConstInt c1 && rhs instanceof ConstInt c2) {
                return foldConstant(binary, c1.getValue(), c2.getValue());
            }
            // 2. 代数化简
            return simplifyAlgebraic(binary, lhs, rhs);
        }
        return null;
    }

    private static Value foldConstant(BinaryInst inst, int v1, int v2) {
        int res = 0;
        boolean isCompare = false;

        switch (inst.getOpCode()) {
            case ADD -> res = v1 + v2;
            case SUB -> res = v1 - v2;
            case MUL -> res = v1 * v2;
            case SDIV -> { if (v2 == 0) return null; res = v1 / v2; }
            case SREM -> { if (v2 == 0) return null; res = v1 % v2; }
            case EQ   -> { res = (v1 == v2) ? 1 : 0; isCompare = true; }
            case NE   -> { res = (v1 != v2) ? 1 : 0; isCompare = true; }
            case SGT  -> { res = (v1 > v2) ? 1 : 0; isCompare = true; }
            case SGE  -> { res = (v1 >= v2) ? 1 : 0; isCompare = true; }
            case SLT  -> { res = (v1 < v2) ? 1 : 0; isCompare = true; }
            case SLE  -> { res = (v1 <= v2) ? 1 : 0; isCompare = true; }
            default -> { return null; }
        }

        if (inst.getType() instanceof IntegerType type) {
            if (isCompare || type.getBitWidth() == 1) {
                res = res & 1;
            }
            return ConstInt.get(type, res);
        }
        return null;
    }

    private static Value simplifyAlgebraic(BinaryInst inst, Value lhs, Value rhs) {
        if (!(inst.getType() instanceof IntegerType type)) return null;

        BinaryOpCode op = inst.getOpCode();
        boolean lZero = isZero(lhs);
        boolean rZero = isZero(rhs);
        boolean lOne = isOne(lhs);
        boolean rOne = isOne(rhs);
        boolean same = (lhs == rhs);

        switch (op) {
            case ADD:
                if (lZero) return rhs;
                if (rZero) return lhs;
                break;
            case SUB:
                if (rZero) return lhs;
                if (same) return ConstInt.get(type, 0);
                break;
            case MUL:
                if (lOne) return rhs;
                if (rOne) return lhs;
                if (lZero || rZero) return ConstInt.get(type, 0);
                break;
            case SDIV:
                if (rOne) return lhs;
                if (same) return ConstInt.get(type, 1);
                break;
            case SREM:
                if (rOne) return ConstInt.get(type, 0);
                if (same) return ConstInt.get(type, 0);
                break;
        }
        return null;
    }

    // --- 哈希计算 (保持不变) ---
    private static String getHash(Instruction inst) {
        if (!(inst instanceof BinaryInst) && !(inst instanceof GepInst)) return null;

        StringBuilder sb = new StringBuilder();
        sb.append(inst.getType().toString()).append("_");

        if (inst instanceof BinaryInst binary) {
            sb.append(binary.getOpCode()).append("_");
            String lKey = getUniqueId(binary.getOperand(0));
            String rKey = getUniqueId(binary.getOperand(1));

            if (isCommutative(binary.getOpCode())) {
                if (lKey.compareTo(rKey) > 0) {
                    sb.append(rKey).append("_").append(lKey);
                } else {
                    sb.append(lKey).append("_").append(rKey);
                }
            } else {
                sb.append(lKey).append("_").append(rKey);
            }
            return sb.toString();
        } else if (inst instanceof GepInst gep) {
            sb.append("GEP_");
            for (int i = 0; i < gep.getNumOperands(); i++) {
                sb.append(getUniqueId(gep.getOperand(i))).append("_");
            }
            return sb.toString();
        }
        return null;
    }

    private static String getUniqueId(Value v) {
        if (v instanceof ConstInt c) {
            return "C" + c.getType().toString() + "_" + c.getValue();
        }
        return "V" + System.identityHashCode(v);
    }

    private static boolean isCommutative(BinaryOpCode op) {
        return switch (op) {
            case ADD, MUL, EQ, NE -> true;
            default -> false;
        };
    }

    private static boolean isZero(Value v) {
        return v instanceof ConstInt c && c.getValue() == 0;
    }

    private static boolean isOne(Value v) {
        return v instanceof ConstInt c && c.getValue() == 1;
    }
}
