package middle;

import middle.component.model.BasicBlock;
import middle.component.model.Function;
import java.util.Stack;

/**
 * 辅助类：用于在 IRBuilder 遍历 AST 时跟踪当前的 IR 生成状态。
 * (这对应于您原始代码中的 IRData 的 loopStack 部分)
 */
public class IRState {

    /**
     * 内部类：用于存储 for 循环的跳转目标。
     * (这对应您原始代码的 ForLoop 类)
     */
    public static class LoopInfo {
        private final BasicBlock continueTarget; // (例如 forStmt2)
        private final BasicBlock breakTarget;    // (例如 followBlock)

        public LoopInfo(BasicBlock continueTarget, BasicBlock breakTarget) {
            this.continueTarget = continueTarget;
            this.breakTarget = breakTarget;
        }
        public BasicBlock getContinueTarget() { return continueTarget; }
        public BasicBlock getBreakTarget() { return breakTarget; }
    }

    private Function currentFunction;
    private BasicBlock currentBlock;
    private final Stack<LoopInfo> loopStack;

    public IRState() {
        this.currentFunction = null;
        this.currentBlock = null;
        this.loopStack = new Stack<>();
    }

    public Function getCurrentFunction() {
        return this.currentFunction;
    }

    public void setCurrentFunction(Function function) {
        this.currentFunction = function;
    }

    public BasicBlock getCurrentBlock() {
        return this.currentBlock;
    }

    /**
     * 设置当前块，并自动将其添加到当前函数
     */
    public void setCurrentBlock(BasicBlock block) {
        // // 提示：
        this.currentBlock = block;
        if (this.currentFunction != null && block.getParent() == null) {
            this.currentFunction.addBasicBlock(block);
        }
    }

    // --- 循环 (For Loop) 栈管理 ---

    public void pushLoop(BasicBlock continueTarget, BasicBlock breakTarget) {
        // 提示：
        this.loopStack.push(new LoopInfo(continueTarget, breakTarget));
    }

    public void popLoop() {
        // 提示：
        this.loopStack.pop();
    }

    public BasicBlock getContinueTarget() {
        // 获取栈顶
        return this.loopStack.peek().getContinueTarget();
    }

    public BasicBlock getBreakTarget() {
        return this.loopStack.peek().getBreakTarget();
    }
}
