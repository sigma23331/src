package backend.utils;

import backend.enums.AsmOp;
import backend.enums.Register;
import backend.text.*;

/**
 * 除法优化工具类
 * 将除法操作转换为 乘法 + 移位 的组合，以提高运行效率。
 * 原理参考 Hacker's Delight 第10章。
 */
public class OptimizedDivision {

    // 内部类：用于保存计算出的魔数和移位量
    private static class MagicData {
        long multiplier; // 魔数 M
        int shift;       // 移位量 S

        public MagicData(long multiplier, int shift) {
            this.multiplier = multiplier;
            this.shift = shift;
        }
    }

    /**
     * 核心入口：将 div src, divisor 优化为乘法序列
     *
     * @param src     被除数所在的寄存器
     * @param divisor 除数（常量）
     * @param dst     结果存放的寄存器
     */
    public static void emitDivOptimization(Register src, int divisor, Register dst) {
        // 1. 处理特殊边界情况
        if (divisor == 1) {
            new MoveAsm(dst, src);
            return;
        }
        if (divisor == -1) {
            new NegAsm(dst, src);
            return;
        }

        // 提取符号，统一按正数处理，最后再根据符号修正
        int absDivisor = Math.abs(divisor);
        boolean isNegative = (divisor < 0);

        // 2. 检查是否为 2 的幂次 (2, 4, 8, 16...)
        // (x & (x - 1)) == 0 是判断 2 的幂的标准位运算技巧
        if ((absDivisor & (absDivisor - 1)) == 0) {
            emitPowerOfTwoDiv(src, absDivisor, dst);
        } else {
            // 3. 通用情况：计算魔数并生成指令
            emitGeneralDiv(src, absDivisor, dst);
        }

        // 4. 如果原除数是负数，结果取反
        if (isNegative) {
            new NegAsm(dst, dst);
        }
    }

    /**
     * 针对除数是 2 的幂次的优化
     * 逻辑：移位代替除法。对于负数被除数，需要偏置修正。
     */
    private static void emitPowerOfTwoDiv(Register src, int absDiv, Register dst) {
        // 计算 log2(absDiv)，即移位位数
        int shiftBits = Integer.numberOfTrailingZeros(absDiv);

        // 偏置修正逻辑：
        // 负数右移是不一致的，所以需要先加上 (2^k - 1)
        // 这里的 tempReg 借用 result 寄存器或者 V0/V1
        Register tempReg = Register.V0;

        // sra $v0, $src, 31  -> 提取符号位 (0 或 -1)
        new CalcAsm(tempReg, AsmOp.SRA, src, 31);

        // 如果 shiftBits > 0，则需要修正掩码
        // srl $v0, $v0, (32 - k) -> 构造偏置值 (2^k - 1)
        new CalcAsm(tempReg, AsmOp.SRL, tempReg, 32 - shiftBits);

        // addu $v1, $src, $v0 -> 被除数 + 偏置
        // 注意：这里我们使用 V1 作为临时存储，避免破坏 src
        Register adjustedSrc = Register.V1;
        new CalcAsm(adjustedSrc, AsmOp.ADDU, src, tempReg);

        // 最后算术右移
        new CalcAsm(dst, AsmOp.SRA, adjustedSrc, shiftBits);
    }

    /**
     * 针对普通整数的优化 (Magic Number)
     */
    private static void emitGeneralDiv(Register src, int absDiv, Register dst) {
        // 计算魔数参数
        MagicData magic = computeMagicParams(absDiv);

        // 将魔数加载到 $v0
        // 注意：magic.multiplier 是 long，但在 32 位指令中我们只取低 32 位
        new LiAsm(Register.V0, (int) magic.multiplier);

        // 根据魔数的大小选择不同的指令序列
        if (magic.multiplier >= 0x80000000L) {
            // 情况 A: 魔数超出了 32 位有符号正数范围
            // 需要配合 mthi 和 madd 指令 (相当于乘法后加一次被除数，修正溢出)
            new MDRegAsm(AsmOp.MTHI, src); // HI = src
            new MulDivAsm(src, AsmOp.MADD, Register.V0); // (HI, LO) = HI + src * magic
        } else {
            // 情况 B: 魔数在范围内，直接乘
            new MulDivAsm(src, AsmOp.MULT, Register.V0); // (HI, LO) = src * magic
        }

        // 取出高位结果 (相当于除以 2^32)
        new MDRegAsm(AsmOp.MFHI, Register.V1);

        // 应用移位修正 (SRA)
        if (magic.shift > 0) {
            new CalcAsm(Register.V0, AsmOp.SRA, Register.V1, magic.shift);
        } else {
            new MoveAsm(Register.V0, Register.V1);
        }

        // 符号位修正：结果 += (src >> 31)
        // 这一步是为了处理负数输入的舍入问题
        new CalcAsm(Register.A0, AsmOp.SRL, src, 31); // 取符号位到 A0
        new CalcAsm(dst, AsmOp.ADDU, Register.V0, Register.A0);
    }

    /**
     * 计算魔数和移位量的核心算法
     * 逻辑源自 Hacker's Delight，无符号除法变种
     */
    private static MagicData computeMagicParams(int d) {
        long divisor = d; // 转为 long 防止计算溢出
        long twoPower31 = 1L << 31; // 2^31

        // nc = ((2^31) - (2^31 % d) - 1) / d
        long limit = (twoPower31 - (twoPower31 % divisor) - 1) / divisor;

        int p = 32; // 初始化 bit 探测位
        long one = 1L;

        // 循环寻找满足条件的最小 p
        // while (2^p <= limit * (d - (2^p % d)))
        while ((one << p) <= (limit * (divisor - ((one << p) % divisor)))) {
            p++;
        }

        // 计算魔数 multiplier = (2^p + d - 2^p % d) / d
        long resMult = ((one << p) + divisor - ((one << p) % divisor)) / divisor;

        // 移位量 shift = p - 32
        int resShift = p - 32;

        return new MagicData(resMult, resShift);
    }
}
