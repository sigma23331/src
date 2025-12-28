package middle;

import middle.component.model.Module;
import optimize.*;
// 导入你未来需要实现的优化 Pass 类
// import optimize.analysis.*;
// import optimize.transform.*;

public class Optimizer {

    private final Module module;

    public Optimizer(Module module) {
        this.module = module;
    }

    public void run() {
        // ==========================================
        // 阶段 1: 预处理 (Preparation)
        // 目标：清理 IR，建立 SSA，为后续优化打基础
        // ==========================================

        // 1. 全局变量本地化
        // 必须在 Mem2Reg 之前。将只在单个函数使用的全局变量变成局部 alloca
        // GlobalVarLocalize.run(module);

        // 2. 第一次 Mem2Reg
        // 消除前端生成的绝大部分 alloca，建立 SSA 形式
        Mem2Reg.run(module, true);

        // 3. 基础死代码消除
        // 删掉 Mem2Reg 后可能留下的无用指令，减轻后续负担
        DeadCodeElimination.run(module);

        // 4. 消除不可达块
        BlockSimplify.run(module);


        // ==========================================
        // 阶段 2: 强力迭代优化 (Iterative Optimization)
        // 目标：通过内联暴露机会，循环榨干性能
        // ==========================================

        boolean changed = true;
        int maxIterations = 5; // 通常 5-10 次足够收敛

        for (int i = 0; i < maxIterations && changed; i++) {
            changed = false;

            // --- Step A: 拓扑结构改变 (Inline) ---
            // 内联必须放在循环开头！
            // 因为内联会引入新的 alloca 和控制流，后续的 Pass 才能优化它。
            InlineFunction.run(module);

            // --- Step B: SSA 修复 (Mem2Reg) ---
            // 内联后，被内联函数的局部变量变成了当前函数的 alloca。
            // 必须再次运行 Mem2Reg 将其提升为寄存器，否则 GVN 分析不到它们。
            Mem2Reg.run(module, true);

            // --- Step C: 算术与冗余消除 (GVN) ---
            // 内联会带来大量的常量传播机会（例如 func(10)）。
            // GVN 负责常量折叠和公共子表达式消除。
            // 注意：你的 GVN 如果包含了 ConstProp，这里就非常强力。
            GVN.run(module);

            // --- Step D: 激进的代码移动 (GCM) ---
            // 在 GVN 清理完冗余后，GCM 将计算移动到循环外或分支内。
            // GCM 依赖 GVN 的简化结果，所以放在 GVN 之后。
            GCM.run(module);

            // --- Step E: 清理 (Cleanup) ---
            // GVN 和 GCM 会导致某些计算结果不再被使用，或者产生死分支。
            DeadCodeElimination.run(module);
            BlockSimplify.run(module);
        }

        // ==========================================
        // 阶段 3: 后处理 (Post-Optimization / Lowering)
        // 目标：为后端生成做最后的清理和准备
        // ==========================================

        // 1. 再次清理死代码 (以防循环最后一次迭代产生了垃圾)
        DeadCodeElimination.run(module);

        // 2. 最终的块简化
        // 合并线性的基本块，减少跳转指令，利于后端生成
        BlockSimplify.run(module);

        // 3. (可选) 消除无副作用的函数调用
        // UnusedFunction.run(module);
    }
}
