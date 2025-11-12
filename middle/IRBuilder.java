package middle;

// --- 导入您所有的 AST 节点 ---
import frontend.syntax.*;
import frontend.syntax.function.*;
import frontend.syntax.statement.*;
import frontend.syntax.expression.*;
import frontend.syntax.variable.*;

// --- 导入我们新创建的 IR Model ---
import middle.component.model.*;
import middle.component.inst.*;
import middle.component.inst.io.*; // (下一步)

// --- 导入我们新创建的 IR Type ---
import middle.component.model.Module;
import middle.component.type.*;

// --- 导入您 Pass 1 的符号表和符号 ---
import middle.symbol.FunctionSymbol;
import middle.component.model.InitialValue;
import middle.symbol.VarSymbol;

// --- 导入我们刚创建的辅助类 ---
import middle.util.NameManager;

import java.util.ArrayList;

public class IRBuilder {

    /**
     * 属性：符号表管理器 (我们的“大脑”)。
     */
    private final ScopeManager scopeManager;

    /**
     * 属性：IR 生成状态 (我们的“记事本”)。
     */
    private final IRState state;

    /**
     * 属性：命名管理器 (我们的“ID生成器”)。
     */
    private final NameManager nameManager;

    /**
     * 属性：顶层 IR 模块 (我们的“根容器”)。
     */
    private final Module module;

    /**
     * 构造函数。
     */
    public IRBuilder(ScopeManager scopeManager) {
        this.scopeManager = scopeManager;
        this.state = new IRState();
        this.nameManager = new NameManager();
        this.module = Module.getInstance();
    }

    /**
     * IR 生成的顶层入口。
     */
    public Module build(CompileUnit ast) {
        // 1. 设置 ScopeManager 到全局作用域 (根)
        //    (这是 Pass 3 的入口点)
        scopeManager.setCurrentScope(scopeManager.getGlobalScope());

        // 2. 为内置函数 (@getint 等) 创建 Function *声明*
        buildBuiltInFunctions();

        // 3. 遍历全局声明 (创建 GlobalVar)
        for (Decl decl : ast.getDecls()) {
            visitGlobalDecl(decl);
        }

        // 4. 遍历函数定义 (创建 Function *定义*)
        for (FuncDef funcDef : ast.getFuncDefs()) {
            // (我们将在下一步实现)
            visitFuncDef(funcDef);
        }

        // 5. 遍历 main 函数
        // (我们将在之后实现)
        visitMainFuncDef(ast.getMainFuncDef());

        return this.module;
    }

    /**
     * (已实现) 创建内置函数的“声明”
     * 这一步从 ScopeManager 中 *解析* 符号，
     * 创建 IR Function *声明*，并将两者链接起来。
     */
    private void buildBuiltInFunctions() {
        // // 提示：
        // // 1. 从 ScopeManager 中 *解析* Pass 1 定义的符号
        // //    (我们假设 Pass 1 定义了 "getint", "putint", "putstr")
        FunctionSymbol getintSym = (FunctionSymbol) scopeManager.resolve("getint");
        FunctionSymbol putintSym = (FunctionSymbol) scopeManager.resolve("putint");
        FunctionSymbol putstrSym = (FunctionSymbol) scopeManager.resolve("putstr");
        //
        // // 2. 为它们创建我们的 IR Function *声明* 对象
        Function getintFunc = new Function(getintSym.getName(),getintSym.getType().getReturnType(),getintSym.getType().getParamTypes(),true);
        Function putintFunc = new Function(putintSym.getName(),putintSym.getType().getReturnType(),putintSym.getType().getParamTypes(),true);
        Function putstrFunc = new Function(putstrSym.getName(),putstrSym.getType().getReturnType(),putstrSym.getType().getParamTypes(),true);
        // // 3. 将它们添加到 Module 中 (Module 现在管理“声明”)
        module.addDeclaration(getintFunc);
        module.addDeclaration(putintFunc);
        module.addDeclaration(putstrFunc);
        //
        // // 4. *关键*：将 IR 对象 (Function) 链接回符号 (Symbol)
        getintSym.setIrValue(getintFunc);
        putintSym.setIrValue(putintFunc);
        putstrSym.setIrValue(putstrFunc);
    }

    /**
     * (新) 访问全局声明 (ConstDecl 或 VarDecl)
     */
    private void visitGlobalDecl(Decl decl) {
        // // 提示：
        // // 全局声明只可能是 ConstDecl 或 VarDecl
        if (decl instanceof  ConstDecl) {
            visitGlobalConstDecl((ConstDecl) decl);
        }
        else if (decl instanceof VarDecl) {
            visitGlobalVarDecl((VarDecl) decl);
        }
    }

    /**
     * (新) 访问全局 ConstDecl
     */
    private void visitGlobalConstDecl(ConstDecl constDecl) {
        for (ConstDef constDef : constDecl.getConstDefs()) {
            // 1. 解析符号
            VarSymbol symbol = (VarSymbol) scopeManager.resolve(constDef.getIdent().getContent());
            // 2. 创建 IR GlobalVar
            PointerType varPtrType = PointerType.get(symbol.getType());
            String varName = "@" + symbol.getName();
            // 3. 创建初始值
            Constant initializer = createGlobalInitializer(symbol);
            // 4. 创建 GlobalVar 对象
            GlobalVar globalVar = new GlobalVar(varName,varPtrType,initializer,true);
            // 5. 添加到 Module 并链接
            module.addGlobalVar(globalVar);
            symbol.setIrValue(globalVar);
        }
    }

    /**
     * (新) 访问全局 VarDecl
     */
    private void visitGlobalVarDecl(VarDecl varDecl) {
        for (VarDef varDef : varDecl.getVarDefs()) {
            VarSymbol symbol = (VarSymbol) scopeManager.resolve(varDef.getIdent().getContent());
            PointerType varPtrType = PointerType.get(symbol.getType());
            String varName = "@" + symbol.getName();
            Constant initializer = createGlobalInitializer(symbol);
            GlobalVar globalVar = new GlobalVar(varName,varPtrType,initializer,false);
            module.addGlobalVar(globalVar);
            symbol.setIrValue(globalVar);
        }
    }

    /**
     * 辅助方法：将 Pass 1 的 InitialValue (来自 VarSymbol) 转换为 IR Constant。
     * 这是处理全局变量和常量的核心。
     *
     * @param symbol 包含 InitialValue 的变量符号
     * @return 一个 IR Constant (ConstInt 或 ConstArray)
     */
    private Constant createGlobalInitializer(VarSymbol symbol) {

        // 从符号中获取 Pass 1 收集的信息
        InitialValue initVal = symbol.getInitialValue();
        Type type = symbol.getType();

        if (type instanceof ArrayType) {
            // --- 情况 1：变量是一个数组 (例如 int a[5]; 或 int a[2] = {1, 2};) ---

            ArrayType arrType = (ArrayType) type;
            IntegerType elemType = (IntegerType) arrType.getElementType();
            int numElements = arrType.getNumElements(); // 数组的总长度 (例如 5)

            // Pass 1 计算出的显式初始值列表 (例如 [1, 2] 或 null)
            ArrayList<Integer> initValues = (initVal != null) ? initVal.getElements() : null;

            // 存储 IR 常量 (例如 ConstInt(1), ConstInt(2))
            ArrayList<Constant> elements = new ArrayList<>();

            for (int i = 0; i < numElements; i++) {
                if (initValues != null && i < initValues.size()) {
                    // A. 如果有显式初始值 (例如 1 和 2)
                    elements.add(ConstInt.get(elemType, initValues.get(i)));
                } else {
                    // B. 如果没有初始值，或者初始值不足 (例如 a[5]={1,2} 中的后3个)
                    //    全局/静态变量默认初始化为 0
                    elements.add(ConstInt.get(elemType, 0));
                }
            }

            // 返回一个 IR 常量数组
            return new ConstArray(arrType, elements);

        } else if (type instanceof IntegerType) {
            // --- 情况 2：变量是一个普通整数 (例如 int a = 10; 或 int a;) ---

            IntegerType intType = (IntegerType) type;

            if (initVal != null && initVal.getElements() != null && !initVal.getElements().isEmpty()) {
                // A. 有显式初始值: int a = 10; (Pass 1 存储为 [10])
                return ConstInt.get(intType, initVal.getElements().get(0));
            } else {
                // B. 无初始值: int a; (全局变量默认为 0)
                return ConstInt.get(intType, 0);
            }
        }

        // (理论上，如果您的语言只有 int 和 int[]，不应该到这里)
        throw new RuntimeException("Unsupported global variable type: " + type);
    }

    /**
     * (新) 访问函数定义 (FuncDef)
     */
    private void visitFuncDef(FuncDef funcDef) {
        // 1. 解析函数符号
        FunctionSymbol functionSymbol = (FunctionSymbol) scopeManager.resolve(funcDef.getIdent().getContent());
        // 2. 创建 IR Function *定义*
        Function irFunction = new Function("@" + functionSymbol.getName(),functionSymbol.getType().getReturnType(),
                functionSymbol.getType().getParamTypes(),false);
        // 3. 添加到 Module 并链接
        module.addFunction(irFunction);
        functionSymbol.setIrValue(irFunction);
        // 4. *关键*：设置当前状态
        state.setCurrentFunction(irFunction);
        scopeManager.setCurrentScope(funcDef.getScope());
        nameManager.reset();
        // 5. 创建入口基本块
        //    例如: "entry:"
        BasicBlock entryBlock = new BasicBlock(
                nameManager.newBlockName("entry"),
                irFunction// (构造函数会自动调用 irFunction.addBasicBlock)
                );
        state.setCurrentBlock(entryBlock);
        // 6. *关键*：为所有参数创建 alloca 和 store
        //    (将 %arg0 存入 %x.addr)
        ArrayList<FuncParam> irParams = irFunction.getParams(); // IR 参数 (例如 %arg0)
        for (int i = 0;i < irParams.size();i++) {
            FuncParam irParam = irParams.get(i);
            // a. 获取此参数在 Pass 1 中的 *局部变量符号*
            String astParamName = funcDef.getFuncFParams().getParams().get(i).getIdent().getContent();
            VarSymbol localParamSymbol = (VarSymbol) scopeManager.resolve(astParamName);
            // b. 创建 AllocInst
            //    例如: "%x.addr = alloca i32"
            String allocName = nameManager.newVarName(astParamName + ".addr");
            AllocInst allocInst = new AllocInst(allocName, irParam.getType());
            entryBlock.addInstruction(allocInst);
            // c. 创建 StoreInst
            // 例如: "store i32 %arg0, i32* %x.addr"
            StoreInst storeInst = new StoreInst(irParam,allocInst);
            entryBlock.addInstruction(storeInst);
            // d. *链接*：将局部符号 "x" 链接到 "%x.addr"
            localParamSymbol.setIrValue(allocInst);
        }
        // 7. 递归访问函数体
        // visitBlock(funcDef.getBlock());
        // 8. *关键*：确保函数有 ret 指令
        BasicBlock lastBlock = state.getCurrentBlock();
        if (lastBlock.getTerminator() == null) {
            if (irFunction.getReturnType() instanceof VoidType) {
                // a. (void 函数)
                RetInst ret = new RetInst();
                lastBlock.addInstruction(ret);
            }
            else {
                // b. (i32 函数) 默认返回 0
                Constant defaultRetVal = ConstInt.get(IntegerType.get32(), 0);
                RetInst ret = new RetInst(defaultRetVal);
                lastBlock.addInstruction(ret);
            }
        }
        // 9. 清理状态
        state.setCurrentFunction(null);
    }

    /**
     * (新) 访问主函数定义 (MainFuncDef)
     */
    private void visitMainFuncDef(MainFuncDef mainFuncDef) {
        // 1. 解析 "main" 符号
        FunctionSymbol functionSymbol = (FunctionSymbol) scopeManager.resolve("main");
        // 2. 创建 IR Function *定义*
        Function irFunction = new Function("@" + functionSymbol.getName(),functionSymbol.getType().getReturnType(),
                functionSymbol.getType().getParamTypes(),false);
        // 3. 添加到 Module 并链接
        module.addFunction(irFunction);
        functionSymbol.setIrValue(irFunction);
        //
        // 4. 设置当前状态
        state.setCurrentFunction(irFunction);
        scopeManager.setCurrentScope(mainFuncDef.getScope()); // 跳转到 main 的作用域
        nameManager.reset();
        // 5. 创建入口基本块
        BasicBlock entryBlock = new BasicBlock(
                nameManager.newBlockName("entry"),
                irFunction// (构造函数会自动调用 irFunction.addBasicBlock)
        );
        state.setCurrentBlock(entryBlock);
        // 6. (无参数，跳过 Alloc/Store)
        // 7. 递归访问函数体
        // visitBlock(mainFuncDef.getBlock());
        // 8. 确保有 "ret i32 0"
        BasicBlock lastBlock = state.getCurrentBlock();
        if (lastBlock.getTerminator() == null) {
            //    例如: "ret i32 0"
            Constant defaultRetVal = ConstInt.get(IntegerType.get32(), 0);
            RetInst ret = new RetInst(defaultRetVal);
            lastBlock.addInstruction(ret);
        }
        // 9. 清理状态
        state.setCurrentFunction(null);
    }

    /**
     * (新) 访问一个基本块 (Block)
     * 对应原始代码的 buildBlock
     */
    private void visitBlock(Block block) {
        // 1. 遍历块中的每一项（声明或语句）
        for (BlockItem item : block.getBlockItems()) {
            visitBlockItem(item);
        }
    }

    /**
     * (新) 访问一个块内项 (BlockItem)
     * 对应原始代码的 buildBlockItem
     */
    private void visitBlockItem(BlockItem item) {
        // 1. 分派到局部声明或语句
        if (item instanceof Decl) {
            visitLocalDecl((Decl) item);
        } else if (item instanceof Stmt) {
            // (我们将在下一步实现 visitStmt)
            // visitStmt((Stmt) item);
        }
    }

    /**
     * (新) 访问一个 *局部* 声明 (Decl)
     * * 关键区别：此方法*只*处理局部变量 (函数内)
     * 对应原始代码的 buildDecl
     */
    private void visitLocalDecl(Decl decl) {
        // // 提示：
        if (decl instanceof ConstDecl) {
            visitLocalConstDecl((ConstDecl) decl);
        } else if (decl instanceof VarDecl) {
            visitLocalVarDecl((VarDecl) decl);
        }
    }

    /**
     * (新) 访问一个 *局部* 常量声明 (ConstDecl)
     * 对应原始代码的 buildConstDef (的 else 分支)
     */
    private void visitLocalConstDecl(ConstDecl constDecl) {
        for (ConstDef constDef : constDecl.getConstDefs()) {
            // 1. 解析符号
            VarSymbol varSymbol = (VarSymbol) scopeManager.resolve(constDef.getIdent().getContent());
            // 2. *关键*：在 *函数入口块* 创建 alloca
            //    例如: "%a.addr.0 = alloca i32"
            String name = nameManager.newVarName(varSymbol.getName() + ".addr");
            AllocInst allocInst = new AllocInst(name, varSymbol.getType());
            state.getCurrentFunction().getEntryBlock().addInstruction(allocInst);
            // 3. 链接符号
            varSymbol.setIrValue(allocInst); // "a" -> "%a.addr.0"
            // 4. 创建初始值
            InitialValue initVal = varSymbol.getInitialValue();
            int constValue = initVal.getElements().get(0); // (假设非数组)
            Constant irConstValue = ConstInt.get(IntegerType.get(32),constValue);
            // 5. 在 *当前块* 创建 store
            //    例如: "store i32 10, i32* %a.addr.0"
            StoreInst storeInst = new StoreInst(irConstValue,allocInst);
            state.getCurrentBlock().addInstruction(storeInst);
        }
    }

    /**
     * (新) 访问一个 *局部* 变量声明 (VarDecl)
     * 对应原始代码的 buildVarDef (的 else 分支)
     */
    private void visitLocalVarDecl(VarDecl varDecl) {
        for (VarDef varDef : varDecl.getVarDefs()) {
             // 提示：
             // 1. 解析符号 (Pass 1 已创建)
             VarSymbol symbol = (VarSymbol) scopeManager.resolve(varDef.getIdent().getContent());
            //     //
             // 2. *关键*：在 *函数入口块* 创建 alloca
             //    例如: "%b.addr.1 = alloca i32"
             String name = nameManager.newVarName(symbol.getName() + ".addr");
             AllocInst alloc = new AllocInst(name, symbol.getType());
             state.getCurrentFunction().getEntryBlock().addInstruction(alloc);
            //     //
             // 3. 链接符号
             symbol.setIrValue(alloc); // "b" -> "%b.addr.1"
            //     //
             // 4. 检查是否有初始值 (例如 int b = 10; 或 int b = c + 1;)
             if (varDef.getInitVal() != null) {
                 // 5. *递归*：访问表达式，生成计算初始值的 IR
                 //    (visitInitVal/visitExp 将在下一步实现)
                 Value initIRValue = visitInitVal(varDef.getInitVal());
            //     //
                 // 6. 在 *当前块* 创建 store
                 //    例如: "store i32 %v5, i32* %b.addr.1"
                 StoreInst store = new StoreInst(initIRValue, alloc);
                 state.getCurrentBlock().addInstruction(store);
             }
             // (如果没有初始值，我们只创建 alloca，不 store)
        }
    }

    /**
     * (新) 访问一个初始值 (InitVal)
     * 对应原始代码的 buildInitVal
     * (我们假设只处理非数组：int a = exp;)
     */
    private Value visitInitVal(InitVal initVal) {
        // // 提示：
        // // 1. 您的 InitVal (来自 Pass 1) 有 getSingleValue()
        // // if (initVal.getSingleValue() != null) {
        //  递归调用 visitExp 生成表达式 IR
        // //     return visitExp(initVal.getSingleValue());
        // // } else if (initVal.getArrayValues() != null) {
        //  (处理数组初始化... 我们暂时跳过)
        // //     return null;
        // // }
        return null; // (暂时占位)
    }

    /**
     * (新) 访问一个表达式 (Exp)
     * 对应原始代码的 buildExp
     */
    private Value visitExp(Exp exp) {
        // // 提示：
        // // (我们将在下一步 6.6 中完整实现)
        // // 暂时只实现顶层
        // // return visitAddExp(exp.getAddExp());
        return null; // (暂时占位)
    }
}
