package middle;

import middle.component.model.Module;
import optimize.*;
// 导入你未来需要实现的优化 Pass 类
// import optimize.analysis.*;
// import optimize.transform.*;

public class Optimizer {

    private final Module module;

    /**
     * 构造函数
     * @param module 经过 IRBuilder 生成的原始模块
     */
    public Optimizer(Module module) {
        this.module = module;
    }

    /**
     * 执行优化流水线
     */
    public void run() {
        // --- 阶段 1: 预处理 (Pre-Optimization) ---
        // 在进行复杂分析前，先清理一下 IR 生成阶段留下的明显垃圾

        // 1. 消除未使用的局部数组 (类似 Reference 的 UnusedLocalArray)
        //    (如果前端生成了大量没人用的局部数组，先删掉)
        // UnusedLocalArray.run(module);

        // 2. 初始的 CFG 简化
        //    (删除不可达的基本块，合并只含跳转的块)
        // BlockSimplify.run(module);


        // --- 阶段 2: 迭代优化循环 (Iterative Optimization) ---
        // 很多优化是相互促进的：
        //   - 常量传播可能会导致某些分支变成死代码。
        //   - 死代码消除后，CFG 结构变简单，可能允许更多函数内联。
        //   - 函数内联后，又带来了新的常量折叠机会。
        // 所以我们需要循环运行。

        boolean changed = true;
        int maxIterations = 10; // 防止无限循环，设置上限

        for (int i = 0; i < maxIterations && changed; i++) {
            // 你可以设置一个标志位，如果某一轮没有任何优化发生，提前 break
            // 这里简化起见，我们强制跑满或者跑固定轮数

            // ===========================
            //      数据流与 SSA 构建
            // ===========================

            // [核心] Mem2Reg: 将 alloca/load/store 提升为寄存器 (构建 SSA)
            // 这是所有高级优化的基础。
            Mem2Reg.run(module,true);

            // 全局变量本地化 (如果一个全局变量只在一个函数里用，把它变局部变量)
            // GlobalVarLocalize.run(module);

            // ===========================
            //      代数化简与消除
            // ===========================

            // GVN (Global Value Numbering): 全局值编号/公共子表达式消除
            // 比如: a = b + c; d = b + c; -> d = a;
            GVN.run(module);

            // GCM (Global Code Motion): 全局代码移动
            // 把计算移动到控制流中尽可能“冷”的地方（比如移出循环）
            GCM.run(module);

            // 常量传播与折叠 (Constant Propagation)
            // ConstProp.run(module);

            // ===========================
            //      过程间优化 (IPO)
            // ===========================

            // 函数内联 (Function Inlining)
            // 将小函数的函数体直接拷贝到调用处，消除函数调用开销
            // InlineFunction.run(module);

            // 消除无用的函数 (Dead Function Elimination)
            // 内联后，原来的被调用函数可能就没人用了
            // UnusedFunction.run(module);

            // ===========================
            //      控制流优化 (CFG)
            // ===========================

            // Icmp 优化 (比如将 a < 0 && a > 10 这种逻辑化简)
            // IcmpOptimize.run(module);

            // 分支预测/块简化 (删除空块，合并块)
            // BlockSimplify.run(module);

            // 死代码消除 (DCE)
            // 删除没有副作用且返回值未被使用的指令
            // DeadCodeElimination.run(module);
        }

        // --- 阶段 3: 后处理 (Post-Optimization) ---

        // 消除无副作用的纯函数调用 (如果还有剩余)
        // FunctionSideEffect.run(module);

        // 这里的 DivideCall 可能是将除法优化为乘法移位 (Strength Reduction)
        // 或者是处理后端不支持的指令
        // DivideCall.run(module);
        DeadCodeElimination.run(module);
        BlockSimplify.run(module);
    }
}
