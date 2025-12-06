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

    // 记录 "HashString -> Value" 的映射
    private static final Map<String, Value> valueNumberMap = new HashMap<>();

    public static void run(Module module) {
        // GVN 依赖支配树信息，如果之前 Pass 破坏了支配树，这里应该重新构建
        // 这里假设 BasicBlock 中已经存储了有效的 domTreeChildren

        for (Function func : module.getFunctions()) {
            if (func.isDeclaration()) continue;

            // 每次处理一个函数，清空哈希表
            valueNumberMap.clear();

            // 从入口块开始进行 RPO (Reverse Post Order) 或者简单的支配树先序遍历
            runOnBlock(func.getEntryBlock());
        }
        DeadCodeElimination.run(module);
    }

    /**
     * 基于支配树的递归遍历
     * 1. 尝试化简当前块的指令
     * 2. 尝试进行 GVN 去重
     * 3. 递归访问支配树的子节点
     * 4. 回溯时清理当前作用域的哈希记录
     */
    private static void runOnBlock(BasicBlock block) {
        // 记录本块中添加的 Hash，用于回溯时删除 (Scope based hash map)
        Set<String> currentScopeHashes = new HashSet<>();

        // 使用副本遍历，因为我们会修改指令列表 (ConcurrentModificationException 防护)
        List<Instruction> instructions = new ArrayList<>(block.getInstructions());

        for (Instruction inst : instructions) {
            // [修正 1] Instruction 没有 isDeleted()。
            // 如果父指针为空，说明在之前的迭代中（如被死代码消除）已经被移除了，跳过。
            if (inst.getParent() == null) continue;

            // --- 步骤 1: 代数化简 & 常量折叠 (InstCombine) ---
            Value simplifiedVal = trySimplify(inst);

            if (simplifiedVal != null) {
                // 如果能简化，直接替换所有引用并删除原指令
                inst.replaceAllUsesWith(simplifiedVal);

                // [修正 2] Instruction 没有 remove() 方法，手动执行移除操作
                inst.removeOperands();                 // 1. 断开操作数连接 (你 Instruction 类里有的)
                inst.getParent().getInstructions().remove(inst); // 2. 从 BasicBlock 列表中移除
                inst.setParent(null);                  // 3. 置空父节点

                continue; // 指令被删了，跳过后续的 GVN 哈希检查
            }

            // --- 步骤 2: GVN (公共子表达式消除) ---
            String hash = getHash(inst);

            if (hash != null) {
                if (valueNumberMap.containsKey(hash)) {
                    // [发现重复]：如果哈希表中已存在，说明在支配节点中计算过该值
                    Value existingVal = valueNumberMap.get(hash);

                    // 用已有的值替换当前指令
                    inst.replaceAllUsesWith(existingVal);

                    inst.remove();
                } else {
                    // [首次出现]：记录到哈希表
                    valueNumberMap.put(hash, inst);
                    currentScopeHashes.add(hash);
                }
            }
        }

        // --- 步骤 3: 递归访问支配树子节点 ---
        // [修正 3] BasicBlock 中的 getter 名字是 getImmediateDominateBlocks
        for (BasicBlock child : block.getImmediateDominateBlocks()) {
            runOnBlock(child);
        }

        // --- 步骤 4: 回溯清理 (恢复现场) ---
        for (String hash : currentScopeHashes) {
            valueNumberMap.remove(hash);
        }
    }

    // ==========================================
    //            辅助逻辑：哈希计算
    // ==========================================

    private static String getHash(Instruction inst) {
        if (inst instanceof BinaryInst binary) {
            String op = binary.getOpCode().toString();
            String lName = binary.getOperand(0).getName();
            String rName = binary.getOperand(1).getName();

            // 处理交换律 (Commutativity)
            // ADD, MUL, AND, OR, XOR 以及部分 ICMP (EQ, NE) 满足交换律
            if (isCommutative(binary.getOpCode())) {
                if (lName.compareTo(rName) > 0) {
                    return rName + " " + op + " " + lName;
                }
            }
            return lName + " " + op + " " + rName;
        }
        else if (inst instanceof GepInst gep) {
            StringBuilder sb = new StringBuilder();
            sb.append("GEP ");
            sb.append(gep.getOperand(0).getName()); // Base pointer
            // 遍历所有索引
            for (int i = 1; i < gep.getNumOperands(); i++) {
                sb.append(" ").append(gep.getOperand(i).getName());
            }
            return sb.toString();
        }
        // 可以扩展 CastInst, GetElementPtr 等
        // CallInst 通常不做 GVN，除非是纯函数

        return null; // 返回 null 表示该指令不参与 GVN
    }

    private static boolean isCommutative(BinaryOpCode op) {
        return switch (op) {
            case ADD, MUL, EQ, NE -> true;
            default -> false;
        };
    }

    // ==========================================
    //            辅助逻辑：代数化简
    // ==========================================

    /**
     * 尝试简化指令。如果能简化，返回简化后的 Value；否则返回 null。
     */
    private static Value trySimplify(Instruction inst) {
        if (inst instanceof BinaryInst binary) {
            Value lhs = binary.getOperand(0);
            Value rhs = binary.getOperand(1);

            // 1. 常量折叠 (Constant Folding): 1 + 2 -> 3
            if (lhs instanceof ConstInt c1 && rhs instanceof ConstInt c2) {
                return foldConstant(binary.getOpCode(), c1.getValue(), c2.getValue());
            }

            // 2. 代数化简 (Algebraic Simplification)
            return simplifyAlgebraic(binary.getOpCode(), lhs, rhs);
        }
        // TODO: 可以添加 Zext/Trunc 的化简逻辑
        return null;
    }

    private static Value foldConstant(BinaryOpCode op, int v1, int v2) {
        int res = 0;
        boolean isCompare = false; // 标记是否为比较运算

        switch (op) {
            // --- 算术运算 (返回 i32) ---
            case ADD -> res = v1 + v2;
            case SUB -> res = v1 - v2;
            case MUL -> res = v1 * v2;
            case SDIV -> res = (v2 == 0) ? 0 : v1 / v2;
            case SREM -> res = (v2 == 0) ? 0 : v1 % v2;

            // --- 比较运算 (返回 i1) ---
            case EQ   -> { res = (v1 == v2) ? 1 : 0; isCompare = true; }
            case NE   -> { res = (v1 != v2) ? 1 : 0; isCompare = true; }
            case SGT  -> { res = (v1 > v2) ? 1 : 0; isCompare = true; }
            case SGE  -> { res = (v1 >= v2) ? 1 : 0; isCompare = true; }
            case SLT  -> { res = (v1 < v2) ? 1 : 0; isCompare = true; }
            case SLE  -> { res = (v1 <= v2) ? 1 : 0; isCompare = true; }

            default -> { return null; }
        }

        // 【关键修复】根据操作类型返回对应的 Type
        if (isCompare) {
            // 比较结果必须是 i1 类型
            // 确保你的 IntegerType 有 get1() 方法，或者使用 new IntegerType(1)
            return ConstInt.get(IntegerType.get1(),res);
        } else {
            // 算术结果是 i32 类型
            return ConstInt.get(IntegerType.get32(),res);
        }
    }

    private static Value simplifyAlgebraic(BinaryOpCode op, Value lhs, Value rhs) {
        // 辅助检查
        boolean lZero = isZero(lhs);
        boolean rZero = isZero(rhs);
        boolean lOne = isOne(lhs);
        boolean rOne = isOne(rhs);
        boolean same = (lhs == rhs); // 此时指针相同即为同一 Value (因为 GVN 保证了唯一性)

        switch (op) {
            case ADD:
                if (lZero) return rhs; // 0 + a -> a
                if (rZero) return lhs; // a + 0 -> a
                break;
            case SUB:
                if (rZero) return lhs; // a - 0 -> a
                if (same) return ConstInt.get(IntegerType.get32(), 0); // a - a -> 0
                break;
            case MUL:
                if (lZero || rZero) return ConstInt.get(IntegerType.get32(), 0); // a * 0 -> 0
                if (lOne) return rhs; // 1 * a -> a
                if (rOne) return lhs; // a * 1 -> a
                break;
            case SDIV:
                if (lZero) return ConstInt.get(IntegerType.get32(), 0); // 0 / a -> 0
                if (rOne) return lhs; // a / 1 -> a
                if (same) return ConstInt.get(IntegerType.get32(), 1); // a / a -> 1
                break;
            case SREM:
                if (lZero || rOne || same) return ConstInt.get(IntegerType.get32(), 0); // 0%a, a%1, a%a -> 0
                break;
        }
        return null;
    }

    private static boolean isZero(Value v) {
        return v instanceof ConstInt c && c.getValue() == 0;
    }

    private static boolean isOne(Value v) {
        return v instanceof ConstInt c && c.getValue() == 1;
    }
}
