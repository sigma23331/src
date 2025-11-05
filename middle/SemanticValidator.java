package middle;

// 1. 导入你的 AST 节点（前端）
import frontend.syntax.*;
import frontend.syntax.expression.*;
import frontend.syntax.function.*;
import frontend.syntax.statement.*;
import frontend.syntax.variable.*;
import frontend.Token.TokenType;

// 2. 导入你的数据结构（第零步）
import middle.symbol.*;
import middle.component.type.*;
import error.ErrorHandler;
import error.Error;
import error.ErrorType;

// 3. 导入 Java 工具
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 语义分析第二遍：语义验证器 (The "Inspector")
 * <p>
 * 职责：
 * 1. 遍历AST，同步 ScopeManager 的作用域。
 * 2. 检查所有“使用时”的语义错误（未定义、类型不匹配、常量赋值等）。
 * 3. 不再向 ScopeManager 中添加任何新符号。
 */
public class SemanticValidator {

    private final ScopeManager scopeManager;
    private final ErrorHandler errorHandler;

    // --- 上下文状态 ---
    // 我们需要跟踪当前所处的函数，以检查 return 语句
    private FunctionSymbol currentFunction = null;
    // 我们需要跟踪循环深度，以检查 break/continue
    private int loopDepth = 0;
    private boolean isGlobal = true;

    /**
     * 构造函数
     * @param scopeManager 在第一遍中被完全填充的 ScopeManager
     */
    public SemanticValidator(ScopeManager scopeManager, ErrorHandler errorHandler) {
        this.scopeManager = scopeManager;
        this.errorHandler = errorHandler;
    }

    /**
     * 遍历的入口
     */
    public void visit(CompileUnit compUnit) {
        // 注意：我们不进入新作用域。全局作用域已在 Pass 1 中创建并保留。
        // 【修改】在开始遍历前，将 ScopeManager 的指针重置到全局作用域
        scopeManager.setCurrentScope(scopeManager.getGlobalScope());
        isGlobal = true;
        for (Decl decl : compUnit.getDecls()) {
            visitDecl(decl);
        }
        // 我们只关心函数体内部的逻辑，全局声明的检查
        // （如常量求值）已在 Pass 1 中完成。
        isGlobal = false;
        for (FuncDef funcDef : compUnit.getFuncDefs()) {
            visitFuncDef(funcDef);
        }
        visitMainFuncDef(compUnit.getMainFuncDef());
    }

    // -----------------------------------------------------------------
    // 作用域和上下文管理
    // -----------------------------------------------------------------

    /**
     * 访问函数定义
     */
    private void visitFuncDef(FuncDef funcDef) {
        // 1. 查找并设置当前函数上下文
        Symbol symbol = scopeManager.resolve(funcDef.getIdent().getContent());
        if (symbol instanceof FunctionSymbol) {
            this.currentFunction = (FunctionSymbol) symbol;
        }

        // 2. 【修改】直接跳转到此函数对应的作用域
        Scope funcScope = funcDef.getScope(); // 从 AST 节点获取在 Pass 1 中挂上的 Scope
        scopeManager.setCurrentScope(funcScope);

        // 3. 访问函数体（所有检查都在这个作用域内发生）
        Block functionBody = funcDef.getBlock();
        visitBlock(functionBody);

        if (currentFunction != null &&
                !(currentFunction.getType().getReturnType() instanceof VoidType)) {
            List<BlockItem> items = functionBody.getBlockItems();
            if (items.isEmpty() ||
                    !(items.get(items.size() - 1) instanceof ReturnStmt)) {
                errorHandler.addError(ErrorType.MissingReturn, functionBody.getFinalToken().getLine());
            }
        }

        // 4. 【修改】退出作用域（将指针移回父作用域）
        scopeManager.exitScope();
        this.currentFunction = null;
    }

    /**
     * 访问 Main 函数
     */
    private void visitMainFuncDef(MainFuncDef mainFuncDef) {
        Symbol symbol = scopeManager.resolve("main");
        if (symbol instanceof FunctionSymbol) {
            this.currentFunction = (FunctionSymbol) symbol;
        }

        // 直接跳转到 main 函数的作用域
        Scope mainFuncScope = mainFuncDef.getScope();
        scopeManager.setCurrentScope(mainFuncScope);

        Block functionBody = mainFuncDef.getBlock();
        visitBlock(functionBody);

        // 【修改】在此处检查 main 函数是否缺少 return
        if (currentFunction != null &&
                !(currentFunction.getType().getReturnType() instanceof VoidType)) {
            List<BlockItem> items = functionBody.getBlockItems();
            if (items.isEmpty() ||
                    !(items.get(items.size() - 1) instanceof ReturnStmt)) {
                errorHandler.addError(ErrorType.MissingReturn, functionBody.getFinalToken().getLine());
            }
        }

        scopeManager.exitScope();
        this.currentFunction = null;
    }

    /**
     * 访问嵌套代码块
     */
    private void visitBlockStmt(BlockStmt blockStmt) {
        // 【修改】直接跳转到此代码块对应的作用域
        Scope blockScope = blockStmt.getScope();
        scopeManager.setCurrentScope(blockScope);

        visitBlock(blockStmt.getBlock());

        scopeManager.exitScope();
    }

    /**
     * 访问代码块内容
     */
    private void visitBlock(Block block) {
        for (BlockItem item : block.getBlockItems()) {
            if (item instanceof Decl) {
                // 检查局部变量的【初始化表达式】
                visitDecl((Decl) item);
            } else if (item instanceof Stmt) {
                visitStmt((Stmt) item);
            }
        }
    }

    /**
     * 访问声明（在 Pass 2）
     * 唯一的任务是检查【初始化表达式】
     */
    private void visitDecl(Decl decl) {
        if (decl instanceof VarDecl) {
            visitVarDecl((VarDecl) decl);
        }
        else if (decl instanceof ConstDecl) {
            visitConstDecl((ConstDecl) decl);
        }
        // ConstDecl 在 Pass 1 中已经连带值一起检查过了，这里可以跳过
    }

    private void visitVarDecl(VarDecl varDecl) {
        for (VarDef varDef : varDecl.getVarDefs()) {

            // 1. 【必须】检查数组维度表达式 (e.g., int a[unknown])
            // 这是你之前修复的漏洞之一。
            if (varDef.getArraySize() != null) {
                // 假设你已经添加了 visitConstExp 来钻取
                visitConstExp(varDef.getArraySize());
            }

            // 2. 【必须】检查初始化表达式 (e.g., int a = unknown)
            //
            // 移除旧的错误逻辑：
            // if (!isGlobal && !varDecl.isStatic() && varDef.getInitVal() != null) { ... }
            //
            // 新逻辑：
            // 无论变量是全局、局部还是静态，只要它有 InitVal，
            // Pass 2 就必须检查这个表达式树。
            if (varDef.getInitVal() != null) {

                // 实验手册规定全局  和静态  变量的
                // InitVal 必须是 ConstExp。
                // 而非静态局部变量可以是 Exp 。

                // 无论如何，visitInitVal 都会钻取到 Exp 节点，
                // 并触发正确的检查链 (visitExp -> ... -> visitLVal)。
                // 所以我们无条件调用它。
                visitInitVal(varDef.getInitVal());
            }
        }
    }

    private void visitInitVal(InitVal initVal) {
        // 递归访问表达式，这会自动触发 visitExp, visitLVal 等检查
        if (initVal.getSingleValue() != null) {
            visitExp(initVal.getSingleValue());
        } else if (initVal.getArrayValues() != null) {
            for (Exp exp : initVal.getArrayValues()) {
                visitExp(exp);
            }
        }
    }

    private void visitConstDecl(ConstDecl constDecl) {
        for (ConstDef constDef : constDecl.getConstDefs()) {
            // 1. 检查数组维度表达式
            if (constDef.getArraySize() != null) {
                visitConstExp(constDef.getArraySize());
            }
            // 2. 检查初始化表达式
            if (constDef.getConstInitVal() != null) {
                visitConstInitVal(constDef.getConstInitVal());
            }
        }
    }

    // 【新增】访问 ConstInitVal
    // 职责：钻取到 ConstExp
    private void visitConstInitVal(ConstInitVal constInitVal) {
        if (constInitVal == null) return;

        if (constInitVal.getSingleValue() != null) {
            visitConstExp(constInitVal.getSingleValue());
        } else if (constInitVal.getArrayValues() != null) {
            for (ConstExp exp : constInitVal.getArrayValues()) {
                visitConstExp(exp);
            }
        }
    }

    // 【新增】访问 ConstExp
    // 职责：连接到共享的 visitAddExp 检查链
    private void visitConstExp(ConstExp constExp) {
        if (constExp != null) {
            // ConstExp 和 Exp 共享相同的 AddExp 结构
            // 复用现有的表达式检查链
            visitAddExp(constExp.getAddExp());
        }
    }


    // -----------------------------------------------------------------
    // 语句检查 (The Core)
    // -----------------------------------------------------------------

    /**
     * 语句分配器
     */
    private void visitStmt(Stmt stmt) {
        // 按类型分配到具体的 visit 方法
        if (stmt instanceof BlockStmt)       visitBlockStmt((BlockStmt) stmt);
        else if (stmt instanceof IfStmt)     visitIfStmt((IfStmt) stmt);
        else if (stmt instanceof ForLoopStmt)  visitForStruct((ForLoopStmt) stmt);
        else if (stmt instanceof AssignStmt) visitAssignStmt((AssignStmt) stmt);
        else if (stmt instanceof ReturnStmt) visitReturnStmt((ReturnStmt) stmt);
        else if (stmt instanceof BreakStmt)  visitBreakStmt((BreakStmt) stmt);
        else if (stmt instanceof ContinueStmt) visitContinueStmt((ContinueStmt) stmt);
        else if (stmt instanceof PrintfStmt) visitPrintfStmt((PrintfStmt) stmt);
        else if (stmt instanceof ExpStmt) visitExpStmt((ExpStmt) stmt);
    }

    /**
     * 检查：表达式语句
     * 作用：递归访问其内部的表达式，以触发函数调用检查等。
     */
    private void visitExpStmt(ExpStmt expStmt) {
        if (expStmt.getExp() != null) {
            visitExp(expStmt.getExp());
        }
    }

    private void visitIfStmt(IfStmt ifStmt) {
        visitCond(ifStmt.getCond()); // 访问条件表达式，检查错误

        visitStmt(ifStmt.getIfStmt());    // 递归访问 then 分支
        if (ifStmt.getElseStmt() != null) {
            visitStmt(ifStmt.getElseStmt());// 递归访问 else 分支
        }
    }

    /**
     * 修正：访问 For 循环
     * 现在它会正确地访问 init, cond, 和 update 语句
     */
    private void visitForStruct(ForLoopStmt forStruct) {

        // ---【这是你的修正】---

        // 1. 访问初始化语句 (ForStmt)
        if (forStruct.getInit() != null) {
            // 调用我们即将创建的新方法
            visitForStmt(forStruct.getInit());
        }

        // 2. 访问条件表达式 (Cond)
        if (forStruct.getCond() != null) {
            // 调用下面的 visitCond 链
            visitCond(forStruct.getCond());
        }

        // 3. 访问更新语句 (ForStmt)
        if (forStruct.getUpdate() != null) {
            // 再次调用我们即将创建的新方法
            visitForStmt(forStruct.getUpdate());
        }
        // --- 修正完毕 ---

        loopDepth++; // 进入循环
        visitStmt(forStruct.getLoopBody()); // 深入循环体
        loopDepth--; // 退出循环
    }

    /**
     * 【新增】访问 ForStmt 节点
     * 语法：ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
     * 它的职责和 visitAssignStmt 完全相同：检查“未定义”和“给常量赋值”。
     */
    private void visitForStmt(ForStmt forStmt) {
        if (forStmt == null) return;

        // 假设你的 ForStmt 节点提供了 LVal 和 Exp 的列表
        // （你需要根据你的 AST 节点 API 来调整 .getLVals() 和 .getExps()）
        List<LVal> lVals = forStmt.getLVals();
        List<Exp> exps = forStmt.getExps();

        // 遍历 "i=0, j=1" 中的所有赋值
        for (int i = 0; i < lVals.size(); i++) {

            LVal lVal = lVals.get(i);
            Exp exp = exps.get(i);

            // --- 复用 visitAssignStmt 的核心逻辑 ---

            // 1. 检查 LVal 本身是否有效（例如是否定义）
            // （visitLVal 会检查未定义、数组维度等）
            visitLVal(lVal);

            // 2. 检查是否为常量
            Symbol symbol = scopeManager.resolve(lVal.getIdent().getContent());
            if (symbol instanceof VarSymbol && ((VarSymbol) symbol).isConstant()) {
                errorHandler.addError(ErrorType.ConstAssign, lVal.getIdent().getLine());
            }

            // 3. 检查右侧表达式（确保它内部没有错误）
            visitExp(exp);
        }
    }

    /**
     * 【新增】访问条件 (Cond)
     * 语法：Cond → LOrExp
     * 职责：启动条件表达式的检查链。
     */
    private void visitCond(Cond cond) {
        if (cond != null && cond.getLOrExp() != null) {
            visitLOrExp(cond.getLOrExp());
        }
    }

    /**
     * 【新增】访问逻辑或 (LOrExp)
     * 语法：LOrExp → LAndExp { '||' LAndExp }
     * 职责：递归访问所有 LAndExp 子节点。
     */
    private void visitLOrExp(LOrExp lOrExp) {
        if (lOrExp != null) {
            // 假设你的 LOrExp 节点有一个 .getLAndExps() 方法
            for (LAndExp lAndExp : lOrExp.getLAndExps()) {
                visitLAndExp(lAndExp);
            }
        }
    }

    /**
     * 【新增】访问逻辑与 (LAndExp)
     * 语法：LAndExp → EqExp { '&&' EqExp }
     * 职责：递归访问所有 EqExp 子节点。
     */
    private void visitLAndExp(LAndExp lAndExp) {
        if (lAndExp != null) {
            // 假设你的 LAndExp 节点有一个 .getEqExps() 方法
            for (EqExp eqExp : lAndExp.getEqExps()) {
                visitEqExp(eqExp);
            }
        }
    }

    /**
     * 【新增】访问相等性 (EqExp)
     * 语法：EqExp → RelExp { ('==' | '!=') RelExp }
     * 职责：递归访问所有 RelExp 子节点。
     */
    private void visitEqExp(EqExp eqExp) {
        if (eqExp != null) {
            // 假设你的 EqExp 节点有一个 .getRelExps() 方法
            for (RelExp relExp : eqExp.getRelExps()) {
                visitRelExp(relExp);
            }
        }
    }

    /**
     * 【新增】访问关系 (RelExp)
     * 语法：RelExp → AddExp { ('<' | '>' | '<=' | '>=') AddExp }
     * 职责：递归访问所有 AddExp 子节点。
     */
    private void visitRelExp(RelExp relExp) {
        if (relExp != null) {
            // 假设你的 RelExp 节点有一个 .getAddExps() 方法
            for (AddExp addExp : relExp.getAddExps()) {
                // --- 汇入点 ---
                // 这里的 visitAddExp 是你之前为 visitExp 实现的
                // 表达式和条件共享了这部分逻辑
                visitAddExp(addExp);
            }
        }
    }

    private void visitBreakStmt(BreakStmt breakStmt) {
        if (loopDepth == 0) {
            errorHandler.addError(ErrorType.NoLoopBreak,breakStmt.getLine());
        }
    }

    private void visitContinueStmt(ContinueStmt continueStmt) {
        if (loopDepth == 0) {
            errorHandler.addError(ErrorType.NoLoopBreak,continueStmt.getLine());
        }
    }

    /**
     * 检查点：给常量赋值 (ConstAssign)
     */
    private void visitAssignStmt(AssignStmt stmt) {
        // 1. 检查 LVal 本身是否有效（例如是否定义）
        visitLVal(stmt.getLVal());

        // 2. 检查是否为常量
        Symbol symbol = scopeManager.resolve(stmt.getLVal().getIdent().getContent());
        if (symbol instanceof VarSymbol && ((VarSymbol) symbol).isConstant()) {
            errorHandler.addError(ErrorType.ConstAssign,stmt.getLine());
        }

        // 3. 检查右侧表达式
        visitExp(stmt.getExp());

        // 4. (高级) 类型检查：左侧类型是否等于右侧类型
        // ...
    }

    /**
     * 检查点：返回值不匹配 (ReturnTypeMismatch)
     */
    private void visitReturnStmt(ReturnStmt returnStmt) {
        if (currentFunction == null) return; // 不在函数内？(理论上不可能)

        Type funcReturnType = currentFunction.getType().getReturnType();

        // Case 1: void 函数带了返回值
        if (funcReturnType instanceof VoidType && returnStmt.getExp() != null) {
            errorHandler.addError(ErrorType.VoidFuncReturn,returnStmt.getLine());
        }

        // Case 2: int 函数返回了 void (已在 visitBlock 末尾检查)

        // Case 3: 访问返回值表达式（这会触发对该表达式的检查）
        if (returnStmt.getExp() != null) {
            visitExp(returnStmt.getExp());
            // (高级) 检查表达式类型是否匹配 funcReturnType
        }
    }

    /**
     * 检查点：Printf 格式化
     */
    private void visitPrintfStmt(PrintfStmt printfStmt) {
        // 1. 访问所有表达式（这会检查它们是否未定义）
        for (Exp exp : printfStmt.getExps()) {
            visitExp(exp);
        }

        // 2. 检查个数是否匹配（逻辑同示例代码）
        int formatCount = 0;
        Pattern pattern = Pattern.compile("%d"); // 假设只支持 %d
        Matcher matcher = pattern.matcher(printfStmt.getFormatString().getContent());
        while (matcher.find()) {
            formatCount++;
        }

        if (formatCount != printfStmt.getExps().size()) {
            errorHandler.addError(ErrorType.PrintfParamMismatch,printfStmt.getLine());
        }
    }

    // -----------------------------------------------------------------
    // 表达式检查 (The Core)
    // -----------------------------------------------------------------

    /**
     * 检查点：函数调用
     */
    private void visitUnaryExp(UnaryExp unaryExp) {
        if (unaryExp == null) return;

        if (unaryExp.getPrimaryExp() != null) {
            visitPrimaryExp(unaryExp.getPrimaryExp());
        } else if (unaryExp.getUnaryExp() != null) {
            visitUnaryExp(unaryExp.getUnaryExp());
        }
        else if (unaryExp.getIdent() != null) {
            // ---------------------------------
            // 这是函数调用，开始检查！
            // ---------------------------------

            if (unaryExp.getIdent().getContent().equals("getint")) { //已经有的就不用考虑了
                return;
            }

            if (unaryExp.getIdent().getContent().equals("printf")) { //已经有的就不用考虑了
                return;
            }

            int line = unaryExp.getIdent().getLine();
            Symbol symbol = scopeManager.resolve(unaryExp.getIdent().getContent(),line);

            // 1. 检查：未定义的名字
            if (symbol == null) {
                errorHandler.addError(ErrorType.UndefinedIdent,unaryExp.getIdent().getLine());
                return; // 后续检查无意义
            }

            // 2. 检查：是否真的是个函数
            if (!(symbol instanceof FunctionSymbol)) {
                errorHandler.addError(ErrorType.UndefinedIdent,unaryExp.getIdent().getLine());
                return;
            }

            FunctionSymbol funcSymbol = (FunctionSymbol) symbol;
            List<Type> expectedTypes = funcSymbol.getType().getParamTypes();

            // 3. 检查：参数个数
            int expectedCount = expectedTypes.size();
            int actualCount = (unaryExp.getRParams() == null) ? 0 :
                    unaryExp.getRParams().getParams().size();

            if (expectedCount != actualCount) {
                errorHandler.addError(ErrorType.FuncParamCountMismatch,unaryExp.getIdent().getLine());
                return; // 个数不对，类型检查无意义
            }

            // 4. 检查：参数类型（维度）
            // 这是简化的类型检查，与示例代码逻辑一致
            if (unaryExp.getRParams() != null) {
                for (int i = 0; i < expectedCount; i++) {
                    Type expectedType = expectedTypes.get(i);
                    Exp actualExp = unaryExp.getRParams().getParams().get(i);

                    // 检查参数的维度
                    int expectedDim = (expectedType instanceof ArrayType) ? 1 : 0; // 假设只有1维
                    int actualDim = getExpressionDimension(actualExp); // 关键辅助方法

                    if (expectedDim != actualDim) {
                        errorHandler.addError(ErrorType.FuncParamTypeMismatch,unaryExp.getIdent().getLine());
                    }

                    // 别忘了递归访问这个参数表达式
                    visitExp(actualExp);
                }
            }
        }
    }

    /**
     * 检查点：未定义的名字 (IdentUndefined)
     */
    private void visitLVal(LVal lVal) {
        int line = lVal.getIdent().getLine();
        // 1. 检查 Ident 是否已定义
        Symbol symbol = scopeManager.resolve(lVal.getIdent().getContent(),line);
        if (symbol == null) {
            errorHandler.addError(ErrorType.UndefinedIdent,lVal.getIdent().getLine());
            return; // 找不到符号，后续检查无意义
        }

        // 2. (高级) 检查：对非数组变量使用下标
        if (lVal.getIndex() != null && !(symbol.getType() instanceof ArrayType)) {
            // 根据需要处理错误
        }

        // 3. 【最终修复】访问索引表达式
        // 这是最关键的修复。必须递归访问下标表达式，
        // 才能捕获 arr[unknown] 中的 'unknown'。
        if (lVal.getIndex() != null) {
            visitExp(lVal.getIndex());
        }
    }

    // --- 表达式的 "穿透" 方法 (现在它们是必需的) ---
    // 它们的作用是把检查传递到树的叶子节点

    private void visitExp(Exp exp) {
        if (exp != null) {
            visitAddExp(exp.getAddExp());
        }
    }

    private void visitAddExp(AddExp addExp) {
        if (addExp != null) {
            for (MulExp mulExp : addExp.getMulExps()) {
                visitMulExp(mulExp);
            }
        }
    }

    private void visitMulExp(MulExp mulExp) {
        if (mulExp != null) {
            for (UnaryExp unaryExp : mulExp.getUnaryExps()) {
                visitUnaryExp(unaryExp);
            }
        }
    }

    private void visitPrimaryExp(PrimaryExp primaryExp) {
        if (primaryExp.getExp() != null) {
            visitExp(primaryExp.getExp());
        } else if (primaryExp.getLVal() != null) {
            visitLVal(primaryExp.getLVal());
        }
        // Number, 啥也不用做
    }

    // -----------------------------------------------------------------
    // 关键辅助方法：获取表达式的“维度”
    // -----------------------------------------------------------------

    /**
     * 辅助方法：计算一个表达式的维度
     * 0: int
     * 1: int[]
     * -1: 错误/Void
     */
    private int getExpressionDimension(Exp exp) {
        // 这是一个简化的类型检查器
        // 我们只关心最顶层的节点

        // 1. 拆包 Exp -> AddExp
        // 注意：你之前的 .toConstExp() 是不正确的，Exp 本身不是 ConstExp
        // 你应该直接调用 .getAddExp()
        AddExp addExp = exp.getAddExp();
        if (addExp.getMulExps().size() > 1) return 0; // 是个加法 1+1，结果是 int

        // 2. 拆包 AddExp -> MulExp
        MulExp mulExp = addExp.getMulExps().get(0);
        if (mulExp.getUnaryExps().size() > 1) return 0; // 是个乘法 2*3，结果是 int

        // 3. 拆包 MulExp -> UnaryExp
        UnaryExp unaryExp = mulExp.getUnaryExps().get(0);

        // 4. 将核心 UnaryExp 交给【辅助函数】去分析
        return getUnaryExpDimension(unaryExp);
    }

    /**
     * 【辅助函数 - 分析器】
     * 作用：分析一个 UnaryExp 的维度，并可以递归调用自身。
     */
    private int getUnaryExpDimension(UnaryExp unaryExp) {

        // Case 1: UnaryOp UnaryExp (e.g., -a)
        if (unaryExp.getUnaryExp() != null) {
            // 【修正】
            // 逻辑："-a" 的维度就是 "a" 的维度。
            // "a" 是 unaryExp.getUnaryExp()，它也是一个 UnaryExp。
            // 所以我们【递归调用自己】。
            return getUnaryExpDimension(unaryExp.getUnaryExp());
        }

        // Case 2: 函数调用
        if (unaryExp.getIdent() != null) {
            Symbol symbol = scopeManager.resolve(unaryExp.getIdent().getContent());
            if (symbol instanceof FunctionSymbol) {
                Type returnType = ((FunctionSymbol) symbol).getType().getReturnType();
                if (returnType instanceof VoidType) return -1; // Void
                if (returnType instanceof ArrayType) return 1; // 返回数组
                return 0; // 返回 int
            }
            return -1; // 未定义或不是函数
        }

        // Case 3: (Exp) | LVal | Number
        PrimaryExp primaryExp = unaryExp.getPrimaryExp();
        if (primaryExp.getExp() != null) {
            // 【修正】
            // 逻辑："(Exp)" 的维度就是 "Exp" 的维度。
            // 我们必须调用【外层函数】来重新开始“拆包”过程。
            return getExpressionDimension(primaryExp.getExp());
        }
        if (primaryExp.getNumber() != null) {
            return 0; // Number
        }
        if (primaryExp.getLVal() != null) {
            // 这是核心
            LVal lVal = primaryExp.getLVal();
            Symbol symbol = scopeManager.resolve(lVal.getIdent().getContent());
            if (symbol instanceof VarSymbol) { // <-- 注意：这里要用 VariableSymbol
                Type type = symbol.getType();
                int baseDim = (type instanceof ArrayType) ? 1 : 0;
                int accessDim = (lVal.getIndex() != null) ? 1 : 0;
                return baseDim - accessDim; // a[] -> 1-0=1; a[1] -> 1-1=0
            }
        }

        return 0; // 默认
    }
}
