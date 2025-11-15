package middle;

// --- 导入您所有的 AST 节点 ---
import frontend.Token.Token;
import frontend.Token.TokenType;
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

    private void buildBuiltInFunctions() {

        // --- 1. 定义 "getint" ---
        // a. 创建 "getint" 的类型： i32 ()
        Type getintRetType = IntegerType.get32();
        ArrayList<Type> getintParams = new ArrayList<>();
        // *** 修正：使用您提供的 public 构造函数 ***
        FunctionType getintType = new FunctionType(getintRetType, getintParams);

        // b. 创建 "getint" 的 IR Function 声明
        //    (我们假设 Function 构造函数 (您已实现) 接收这些参数)
        Function getintFunc = new Function(
                "getint",
                getintRetType,
                getintParams,
                true // isDeclaration = true
        );

        // c. 创建 "getint" 的 FunctionSymbol
        //    (这匹配您提供的 FunctionSymbol 构造函数)
        FunctionSymbol getintSym = new FunctionSymbol("getint", getintType, new ArrayList<>(), 0); // (line 0)

        // d. 链接并注册
        getintSym.setIrValue(getintFunc);         // 链接 Symbol -> IR Value
        module.addDeclaration(getintFunc);        // 添加到 Module
        scopeManager.define(getintSym, 0);        // *关键*：注入到全局作用域


        // --- 2. 定义 "putint" ---
        // a. 创建 "putint" 的类型： void (i32)
        Type putintRetType = VoidType.getInstance();
        ArrayList<Type> putintParams = new ArrayList<>();
        putintParams.add(IntegerType.get32());
        // *** 修正：使用您提供的 public 构造函数 ***
        FunctionType putintType = new FunctionType(putintRetType, putintParams);

        // b. 创建 "putint" 的 IR Function 声明
        Function putintFunc = new Function(
                "putint",
                putintRetType,
                putintParams,
                true // isDeclaration = true
        );

        // c. 创建 "putint" 的 FunctionSymbol
        FunctionSymbol putintSym = new FunctionSymbol("putint", putintType, new ArrayList<>(), 0);

        // d. 链接并注册
        putintSym.setIrValue(putintFunc);
        module.addDeclaration(putintFunc);
        scopeManager.define(putintSym, 0);


        // --- 3. 定义 "putstr" ---
        // a. 创建 "putstr" 的类型： void (i8*)
        Type putstrRetType = VoidType.getInstance();
        ArrayList<Type> putstrParams = new ArrayList<>();
        putstrParams.add(PointerType.get(IntegerType.get8())); // i8*
        // *** 修正：使用您提供的 public 构造函数 ***
        FunctionType putstrType = new FunctionType(putstrRetType, putstrParams);

        // b. 创建 "putstr" 的 IR Function 声明
        Function putstrFunc = new Function(
                "putstr",
                putstrRetType,
                putstrParams,
                true // isDeclaration = true
        );

        // c. 创建 "putstr" 的 FunctionSymbol
        FunctionSymbol putstrSym = new FunctionSymbol("putstr", putstrType, new ArrayList<>(), 0);

        // d. 链接并注册
        putstrSym.setIrValue(putstrFunc);
        module.addDeclaration(putstrFunc);
        scopeManager.define(putstrSym, 0);
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
        Function irFunction = new Function(functionSymbol.getName(),functionSymbol.getType().getReturnType(),
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
        for (FuncParam irParam : irParams) {
            // (从 nameManager 获取 %v0, %v1 ...)
            String paramName = nameManager.newVarName();
            irParam.setName(paramName);
        }
        for (int i = 0;i < irParams.size();i++) {
            FuncParam irParam = irParams.get(i);
            // a. 获取此参数在 Pass 1 中的 *局部变量符号*
            String astParamName = funcDef.getFuncFParams().getParams().get(i).getIdent().getContent();
            VarSymbol localParamSymbol = (VarSymbol) scopeManager.resolve(astParamName);
            // b. 创建 AllocInst
            if (localParamSymbol.getType() instanceof ArrayType) {
                // --- 情況 A：這是一個數組/指針參數 (int a[]) ---
                // (設計決策：我們將符號 "a" *直接* 鏈接到 IR 參數 %v0)
                // (這與您的原始 IRBuilder 邏輯一致)

                // *我們不* 創建 AllocInst 或 StoreInst

                // d. *鏈接*：將局部符號 "a" 鏈接到 IR 參數 "%v0"
                localParamSymbol.setIrValue(irParam);

            } else {
                // --- 情況 B：這是一個標量參數 (int n) ---
                // (您現有的 alloca-and-store 邏輯是正確的)

                // b. 創建 AllocInst
                String allocName = nameManager.newVarName(astParamName + ".addr");
                AllocInst allocInst = new AllocInst(allocName, irParam.getType());
                entryBlock.addInstruction(allocInst);

                // c. 創建 StoreInst
                StoreInst storeInst = new StoreInst(irParam, allocInst);
                entryBlock.addInstruction(storeInst);

                // d. *鏈接*：將局部符號 "n" 鏈接到 "%n.addr"
                localParamSymbol.setIrValue(allocInst);
            }
        }
        // 7. 递归访问函数体
        visitBlock(funcDef.getBlock());
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
        Function irFunction = new Function(functionSymbol.getName(),functionSymbol.getType().getReturnType(),
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
        visitBlock(mainFuncDef.getBlock());
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
        if (state.getCurrentBlock().getTerminator() != null) {
            // 該塊已死。
            // 之後的所有語句 (例如 `return x;`) 都是不可達代碼。
            // 我們忽略它們，什麼也不做。
            return; // (!!!)
        }
        // 1. 分派到局部声明或语句
        if (item instanceof Decl) {
            visitLocalDecl((Decl) item);
        } else if (item instanceof Stmt) {
            // (我们将在下一步实现 visitStmt)
            visitStmt((Stmt) item);
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
            VarSymbol symbol = (VarSymbol) scopeManager.resolve(constDef.getIdent().getContent());

            // 2. 在 *函数入口块* 创建 alloca
            String name = nameManager.newVarName(symbol.getName() + ".addr");
            AllocInst alloc = new AllocInst(name, symbol.getType());
            state.getCurrentFunction().getEntryBlock().addInstruction(alloc);

            // 3. 链接符号
            symbol.setIrValue(alloc); // "a" -> "%a.addr.0"

            // 4. 创建初始值 (从 Pass 1 的 InitialValue 中获取)
            //    (注意：我们调用 visitInitVal 来统一处理)
            ArrayList<Value> initIRValues = visitInitVal(constDef.getConstInitVal(), symbol);

            // 5. 在 *当前块* 创建 store
            if (symbol.getType() instanceof ArrayType) {
                // --- 5a. 是数组: int a[2] = {1, 2}; ---
                // 调用辅助函数循环 GEP + Store
                storeArrayInit(alloc, initIRValues);
            } else {
                // --- 5b. 是标量: const int a = 10; ---
                //    例如: "store i32 10, i32* %a.addr.0"
                StoreInst store = new StoreInst(initIRValues.get(0), alloc);
                state.getCurrentBlock().addInstruction(store);
            }
        }
    }

    /**
     * (新) 访问一个 *局部* 变量声明 (VarDecl)
     * 对应原始代码的 buildVarDef (的 else 分支)
     */
    private void visitLocalVarDecl(VarDecl varDecl) {
        for (VarDef varDef : varDecl.getVarDefs()) {
            // 1. 解析符号
            VarSymbol symbol = (VarSymbol) scopeManager.resolve(varDef.getIdent().getContent());

            if (symbol.isStatic()) {
                // --- 情況 1：是 static 局部變量 ---
                // (我們必須像處理 *全局* 變量一樣處理它)

                // a. 創建 GlobalVar
                PointerType varPtrType = PointerType.get(symbol.getType());

                // (給它一個唯一的內部名字，例如 @testNormalExpr.temp_Two)
                String varName = "@" + state.getCurrentFunction().getName() + "." + symbol.getName();
                varName = nameManager.newGlobalName(varName);
                // b. 獲取初始值 (如果未初始化，createGlobalInitializer 會返回 0)
                Constant initializer = createGlobalInitializer(symbol);

                // c. 創建 GlobalVar (非 const)
                GlobalVar gv = new GlobalVar(varName, varPtrType, initializer, false);

                // d. 添加到 Module 並鏈接
                module.addGlobalVar(gv);
                symbol.setIrValue(gv); // 符號 "temp_Two" -> "@testNormalExpr.temp_Two"

            }
            else {
                // 2. 在 *函数入口块* 创建 alloca
                String name = nameManager.newVarName(symbol.getName() + ".addr");
                AllocInst alloc = new AllocInst(name, symbol.getType());
                state.getCurrentFunction().getEntryBlock().addInstruction(alloc);

                // 3. 链接符号
                symbol.setIrValue(alloc); // "b" -> "%b.addr.1"

                // 4. 检查是否有初始值
                if (varDef.getInitVal() != null) {
                    // 5. *递归*：访问表达式，生成计算初始值的 IR
                    //    (visitInitVal 现在会返回一个列表)
                    ArrayList<Value> initIRValues = visitInitVal(varDef.getInitVal(), symbol);

                    // 6. 在 *当前块* 创建 store
                    if (symbol.getType() instanceof ArrayType) {
                        // --- 6a. 是数组: int a[2] = {b, c+1}; ---
                        storeArrayInit(alloc, initIRValues);
                    } else {
                        // --- 6b. 是标量: int a = b + 1; ---
                        //    例如: "store i32 %v5, i32* %b.addr.1"
                        StoreInst store = new StoreInst(initIRValues.get(0), alloc);
                        state.getCurrentBlock().addInstruction(store);
                    }
                }
            }
        }
    }

    /**
     * (新) 辅助方法：生成 GEP 和 Store 来初始化一个局部数组
     * @param alloc 数组的 AllocInst (例如 [2 x i32]*)
     * @param initIRValues 计算好的初始值列表 (例如 [%v1, %v2])
     */
    private void storeArrayInit(AllocInst alloc, ArrayList<Value> initIRValues) {
        // 1. 获取数组的元素类型 (例如 i32)
        Type elemType = ((ArrayType) alloc.getAllocatedType()).getElementType();
        // 2. 循环遍历 *所有* 初始值
        for (int i = 0;i < initIRValues.size();i++) {
            // 3. 获取第 i 个初始值 (例如 %v2)
            Value elemValue = initIRValues.get(i);
            // 4. 创建 GEP 指令
            //    例如: "%ptr.2 = gep [2 x i32]*, %b.addr.1, i32 0, i32 0" (i=0)
            //    (注意：GEP 需要两个索引：0 用于 "穿透" 指针, i 用于访问元素)
            Value idx0 = ConstInt.get(IntegerType.get32(),0);
            Value idxI = ConstInt.get(IntegerType.get32(),i);
            String gepName = nameManager.newVarName();

            GepInst gep = new GepInst(gepName, alloc, new ArrayList<>(java.util.List.of(idx0, idxI)));
            state.getCurrentBlock().addInstruction(gep);
            // 5. 创建 Store 指令
            //    例如: "store i32 %v2, i32* %ptr.2"
            StoreInst store = new StoreInst(elemValue,gep);
            state.getCurrentBlock().addInstruction(store);
        }
    }

    /**
     * (修正) 访问一个初始值 (ConstInitVal 或 InitVal)
     * (对应 原始代码 buildInitVal)
     * *** 现已支持数组 ***
     * @param initValNode AST 节点 (ConstInitVal 或 InitVal)
     * @param symbol 对应的 VarSymbol (用于获取类型信息)
     * @return 一个 IR Value 列表
     */
    private ArrayList<Value> visitInitVal(Object initValNode, VarSymbol symbol) {
        // // 提示：
        ArrayList<Value> irValues = new ArrayList<>();
        Type type = symbol.getType();

        if (initValNode instanceof ConstInitVal) {
            // --- 情况 1：常量初始化 (const int a = ... / const int a[2] = ...) ---
            // (我们 *不* 需要递归，因为 Pass 1 已经计算好了)
            InitialValue constInit = symbol.getInitialValue(); // 来自 Pass 1

            if (type instanceof ArrayType) {
                // 数组: int a[2] = {1, 2};
                IntegerType elemType = (IntegerType) ((ArrayType) type).getElementType();
                for (int val : constInit.getElements()) {
                    irValues.add(ConstInt.get(elemType, val));
                }
            } else {
                // 标量: int a = 10;
                irValues.add(ConstInt.get((IntegerType) type, constInit.getElements().get(0)));
            }

        } else if (initValNode instanceof InitVal) {
            // --- 情况 2：变量初始化 (int a = ... / int a[2] = ...) ---
            InitVal initVal = (InitVal) initValNode;

            if (initVal.getSingleValue() != null) {
                // 标量: int a = b + 5;
                // *必须* 递归调用 visitExp
                irValues.add(visitExp(initVal.getSingleValue()));

            } else if (initVal.getArrayValues() != null) {
                // 数组: int a[2] = {b, c + 1};
                // *必须* 为每个元素递归调用 visitExp
                for (Exp exp : initVal.getArrayValues()) {
                    irValues.add(visitExp(exp));
                }
            }
        }
        return irValues;
    }

    /**
     * (已实现) 访问一个表达式 (Exp)
     * (对应 原始代码 buildExp)
     */
    private Value visitExp(Exp exp) {
        // 表达式的根节点是 AddExp
        return visitAddExp(exp.getAddExp());
    }

    /**
     * (已实现) 访问 AddExp (+, -)
     * (对应 原始代码 buildAddExp)
     */
    private Value visitAddExp(AddExp addExp) {
        // 1. 访问第一个 MulExp
        Value left = visitMulExp(addExp.getMulExps().get(0));

        // 2. 循环处理
        for (int i = 1; i < addExp.getMulExps().size(); i++) {
            // 3. 获取操作符
            TokenType op = addExp.getOperators().get(i - 1).getType();

            // 4. 访问右侧的 MulExp
            Value right = visitMulExp(addExp.getMulExps().get(i));

            // 5. 确定 OpCode (ADD 或 SUB)
            BinaryOpCode opCode = (op == TokenType.PLUS) ? BinaryOpCode.ADD : BinaryOpCode.SUB;

            // 6. 创建 BinaryInst
            //    例如: "%v1 = add i32 %v0, %temp"
            String name = nameManager.newVarName();
            BinaryInst inst = new BinaryInst(opCode, left, right);
            inst.setName(name);
            state.getCurrentBlock().addInstruction(inst);

            // 7. 将此指令的结果作为下一次循环的 left
            left = inst;
        }

        return left; // 返回最终的计算结果 (Value)
    }

    /**
     * (已实现) 访问 MulExp (*, /, %)
     * (对应 原始代码 buildMulExp)
     */
    private Value visitMulExp(MulExp mulExp) {
        // 1. 访问第一个 UnaryExp
        Value left = visitUnaryExp(mulExp.getUnaryExps().get(0));

        // 2. 循环处理
        for (int i = 1; i < mulExp.getUnaryExps().size(); i++) {
            TokenType op = mulExp.getOperators().get(i - 1).getType();
            Value right = visitUnaryExp(mulExp.getUnaryExps().get(i));

            // 3. 确定 OpCode
            BinaryOpCode opCode;
            if (op == TokenType.MULT) {
                opCode = BinaryOpCode.MUL;
            } else if (op == TokenType.DIV) {
                opCode = BinaryOpCode.SDIV;
            } else { // (op == TokenType.MOD)
                opCode = BinaryOpCode.SREM;
            }

            // 4. 创建 BinaryInst
            //    例如: "%v2 = mul i32 %v1, 5"
            String name = nameManager.newVarName();
            BinaryInst inst = new BinaryInst(opCode, left, right);
            inst.setName(name);
            state.getCurrentBlock().addInstruction(inst);

            // 5. 更新 left
            left = inst;
        }

        return left;
    }

    /**
     * (已实现) 访问 UnaryExp (-, !, +, func(), PrimaryExp)
     * (对应 原始代码 buildUnaryExp)
     */
    private Value visitUnaryExp(UnaryExp unaryExp) {
        if (unaryExp.getPrimaryExp() != null) {
            // --- 情况 1：(Exp), LVal, Number ---
            return visitPrimaryExp(unaryExp.getPrimaryExp());

        } else if (unaryExp.getUnaryOp() != null) {
            // --- 情况 2：+, -, ! ---
            Value value = visitUnaryExp(unaryExp.getUnaryExp());
            TokenType op = unaryExp.getUnaryOp().getOp().getType();

            if (op == TokenType.PLUS) {
                return value; // ( +a 等于 a )
            }

            // (创建 "0")
            Constant zero = ConstInt.get(IntegerType.get32(), 0);
            String name = nameManager.newVarName();

            if (op == TokenType.MINU) {
                // -a 编译为 "0 - a"
                //    例如: "%v3 = sub i32 0, %v2"
                BinaryInst inst = new BinaryInst(BinaryOpCode.SUB, zero, value);
                inst.setName(name);
                state.getCurrentBlock().addInstruction(inst);
                return inst;
            } else { // (op == TokenType.NOT)
                // !a 编译为 "a == 0" (返回 i1)
                //    例如: "%v4 = icmp eq i32 %v3, 0"
                BinaryInst inst = new BinaryInst(BinaryOpCode.EQ, value, zero);
                inst.setName(name);
                state.getCurrentBlock().addInstruction(inst);

                // *关键*：!a 返回 0 或 1 (i32)，但 icmp 返回 i1 (bool)
                // 我们需要 zext (零扩展)
                //    例如: "%v5 = zext i1 %v4 to i32"
                String zextName = nameManager.newVarName();
                ZextInst zext = new ZextInst(zextName, inst, IntegerType.get32());
                state.getCurrentBlock().addInstruction(zext);
                return zext;
            }

        } else if (unaryExp.getIdent() != null) {
            // --- 情况 3：函数调用 (例如 getint() 或 myFunc(a, 10)) ---

            // a. 解析函数符号
            String funcName = unaryExp.getIdent().getContent();
            FunctionSymbol funcSym = (FunctionSymbol) scopeManager.resolve(funcName);
            Function funcToCall = (Function) funcSym.getIrValue(); // (来自 buildBuiltInFunctions)

            // b. 递归访问所有 *参数* (RParams)
            ArrayList<Value> args = new ArrayList<>();
            if (unaryExp.getRParams() != null) {
                for (Exp argExp : unaryExp.getRParams().getParams()) {
                    args.add(visitExp(argExp));
                }
            }

            // c. 创建 CallInst
            String name = "";
            // (如果函数不返回 void，我们需要一个名字来存储结果)
            if (!(funcToCall.getReturnType() instanceof VoidType)) {
                name = nameManager.newVarName();
            }

            // (为 getint() 创建特殊的子类)
            if (funcName.equals("getint")) {
                GetintInst call = new GetintInst(name, funcToCall);
                state.getCurrentBlock().addInstruction(call);
                return call;
            }
            // (putint, putstr 在 printf 中处理)
            else {
                // (普通函数调用)
                CallInst call = new CallInst(name, funcToCall, args);
                state.getCurrentBlock().addInstruction(call);
                return call;
            }
        }
        return null; // (不应到达)
    }

    /**
     * (已实现) 访问 PrimaryExp ( (Exp), LVal, Number )
     * (对应 原始代码 buildPrimaryExp)
     */
    private Value visitPrimaryExp(PrimaryExp primaryExp) {
        if (primaryExp.getExp() != null) {
            // --- 情况 1：(Exp) ---
            return visitExp(primaryExp.getExp());

        } else if (primaryExp.getNumber() != null) {
            // --- 情况 2：Number ---
            int val = Integer.parseInt(primaryExp.getNumber().getIntConst().getContent());
            return ConstInt.get(IntegerType.get32(), val);

        } else if (primaryExp.getLVal() != null) {
            // --- 情况 3：LVal (变量使用) ---
            // *关键*：当 LVal 出现在表达式中时，我们必须 *加载* (load) 它的值
            return visitLValValue(primaryExp.getLVal());
        }
        return null; // (不应到达)
    }

    /**
     * (已实现) 访问 LVal (作为 *值* 使用)
     * * 关键*：生成 Load 指令
     * *** 最终修正版：正确处理数组指针退化 ***
     */
    private Value visitLValValue(LVal lVal) {
        // 1. 解析 LVal 符号
        VarSymbol symbol = (VarSymbol) scopeManager.resolve(lVal.getIdent().getContent(),lVal.getIdent().getLine());

        // 2. 获取该变量的 *地址* (指针)
        Value pointer = symbol.getIrValue(); // (例如 %a.addr.0 或 %v0)

        if (symbol.getType() instanceof ArrayType) {

            // 4. *关键*：检查 IR 指针的实际类型 (它指向什么)
            Type pointeeType = ((PointerType) pointer.getType()).getPointeeType();

            if (lVal.getIndex() != null) {
                // --- 情况 2a：数组访问 (例如 `a[i]`) ---

                // a. 递归访问索引表达式
                Value index = visitExp(lVal.getIndex());

                // b. *修正*：构建索引列表
                ArrayList<Value> indices = new ArrayList<>();
                if (pointeeType instanceof ArrayType) {
                    // (这是 %a.addr [10 x i32]*, 需要 2 个索引 [0, i])
                    indices.add(ConstInt.get(IntegerType.get32(), 0));
                    indices.add(index);
                } else {
                    // (这是 %v0 i32*, 只需要 1 个索引 [i])
                    indices.add(index);
                }

                // c. 创建 GEP 指令
                String gepName = nameManager.newVarName();
                GepInst gep = new GepInst(gepName, pointer, indices);
                state.getCurrentBlock().addInstruction(gep);

                // d. 创建 Load 指令
                String loadName = nameManager.newVarName();
                LoadInst load = new LoadInst(loadName, gep);
                state.getCurrentBlock().addInstruction(load);
                return load; // (返回 i32)

            } else {
                // --- 情况 2b：数组名 (例如 `myFunc(a)`) ---

                if (pointeeType instanceof ArrayType) {
                    // (这是 %a.addr [10 x i32]*, 需要 GEP [0, 0] 来退化为 i32*)
                    Value idx0 = ConstInt.get(IntegerType.get32(), 0);
                    String gepName = nameManager.newVarName();
                    GepInst gep = new GepInst(gepName, pointer, new ArrayList<>(java.util.List.of(idx0, idx0)));
                    state.getCurrentBlock().addInstruction(gep);
                    return gep; // (返回 i32*)
                } else {
                    // (这是 %v0 i32*, 它 *已经* 是退化后的指针了)
                    // (什么都不用做，直接返回它)
                    return pointer; // (返回 i32*)
                }
            }

        } else {
            // --- 情况 1：标量变量 (例如 `a`) ---
            String name = nameManager.newVarName();
            LoadInst load = new LoadInst(name, pointer);
            state.getCurrentBlock().addInstruction(load);
            return load; // (返回 i32)
        }
    }

    /**
     * (已实现) 访问 LVal (作为 *赋值* 目标)
     * * 关键*：*不* 生成 Load，只返回地址
     * (对应 原始代码 buildLValAssign)
     */
    private Value visitLValAssign(LVal lVal) {
        // 1. 解析 LVal 符号
        VarSymbol symbol = (VarSymbol) scopeManager.resolve(lVal.getIdent().getContent(),lVal.getIdent().getLine());

        // 2. 获取该变量的 *基地址* (指针)
        Value basePointer = symbol.getIrValue(); // (例如 %a.addr.0 或 %v0)

        if (symbol.getType() instanceof ArrayType) {
            // --- 情况 2：数组 (例如 a[i] = ...) ---

            // 3. *关键*：检查 IR 指针的实际类型 (它指向什么)
            Type pointeeType = ((PointerType) basePointer.getType()).getPointeeType();

            // a. 递归访问索引表达式
            Value index = visitExp(lVal.getIndex());

            // b. *修正*：构建索引列表
            ArrayList<Value> indices = new ArrayList<>();
            if (pointeeType instanceof ArrayType) {
                // (这是 %a.addr [10 x i32]*, 需要 2 个索引 [0, i])
                indices.add(ConstInt.get(IntegerType.get32(), 0));
                indices.add(index);
            } else {
                // (这是 %v0 i32*, 只需要 1 个索引 [i])
                indices.add(index);
            }

            // c. 创建 GEP 指令
            String gepName = nameManager.newVarName();
            // (注意：你的 GepInst 构造函数需要能正确处理 basePointer 和 indices)
            GepInst gep = new GepInst(gepName, basePointer, indices);
            state.getCurrentBlock().addInstruction(gep);

            // d. 返回 GEP 的结果 (地址)
            return gep; // (例如 %ptr.4)

        } else {
            // --- 情况 1：标量 (例如 a = ...) ---
            //    直接返回 alloca 的地址
            return basePointer; // (例如 %a.addr.0)
        }
    }

    /**
     * (新) 访问一个语句 (Stmt) - 这是一个分派器
     * (对应 原始代码 buildStmt)
     */
    private void visitStmt(Stmt stmt) {
        // // 提示：
        // // 这是一个分派器，根据 Stmt 的实际类型，
        // // 调用下面更具体的 visit 方法。
        //
        if (stmt instanceof BlockStmt) {
            // 例如: { ... }
            visitBlockStmt((BlockStmt) stmt);
        } else if (stmt instanceof AssignStmt) {
            // 例如: a = 10;
            visitAssignStmt((AssignStmt) stmt);
        } else if (stmt instanceof ExpStmt) {
            // 例如: a + 1; (有副作用，如函数调用)
            visitExpStmt((ExpStmt) stmt);
        } else if (stmt instanceof PrintfStmt) {
            // 例如: printf("%d", a);
            visitPrintfStmt((PrintfStmt) stmt);
        } else if (stmt instanceof ReturnStmt) {
            // 例如: return a;
            visitReturnStmt((ReturnStmt) stmt);
        } else if (stmt instanceof IfStmt) {
            // 例如: if (a > 0) ...
            visitIfStmt((IfStmt) stmt);
        } else if (stmt instanceof ForLoopStmt) { // (根据您的 AST 节点类名修改)
            // 例如: for (i = 0; i < 10; i = i + 1) ...
            visitForStmt((ForLoopStmt) stmt);
        } else if (stmt instanceof BreakStmt) {
            // 例如: break;
            visitBreakStmt((BreakStmt) stmt);
        } else if (stmt instanceof ContinueStmt) {
            // 例如: continue;
            visitContinueStmt((ContinueStmt) stmt);
        }
    }

    /**
     * (新) 访问一个块语句 (BlockStmt)
     * (对应 原始代码 buildStmt 的 BlockStmt 分支)
     */
    private void visitBlockStmt(BlockStmt blockStmt) {
        // 1. *关键*：进入新的作用域。
        //    从 AST 节点获取 Pass 1 创建的 Scope。
        scopeManager.setCurrentScope(blockStmt.getScope());
        // // 2. 递归访问该块的内容
        visitBlock(blockStmt.getBlock());
        // // 3. *关键*：退出作用域，返回到父作用域。
        scopeManager.exitScope();
    }

    /**
     * (新) 辅助方法：构建一个赋值操作
     * *** 这是为解决 ForStmt 冲突而新增的 ***
     */
    private void buildAssignment(LVal lVal, Exp exp) {
        // // 提示：
        // // 1. 递归访问 *右侧* 表达式，获取计算结果
        // //    例如: (b + 5) -> %v5
        Value rValue = visitExp(exp);
        //
        // // 2. 递归访问 *左侧* LVal，获取 *地址*
        // //    (调用我们已实现的 visitLValAssign)
        // //    例如: a[i] -> %ptr.4
        Value lValuePointer = visitLValAssign(lVal);
        //
        // // 3. 创建 Store 指令
        // //    例如: "store i32 %v5, i32* %ptr.4"
        StoreInst store = new StoreInst(rValue, lValuePointer);
        state.getCurrentBlock().addInstruction(store);
    }

    /**
     * (修正) 访问一个赋值语句 (LValExpStmt)
     * *** 现在调用新的辅助方法 ***
     */
    private void visitAssignStmt(AssignStmt stmt) {
        // // 提示：
        // // 1. 直接调用我们的新辅助方法
        buildAssignment(stmt.getLVal(), stmt.getExp());
    }

    /**
     * (新) 访问一个 printf 语句 (PrintfStmt)
     * *** 这是核心的“拆解”逻辑 ***
     * (对应 原始代码 buildPrintfStmt)
     */
    private void visitPrintfStmt(PrintfStmt stmt) {
        // // 1. 解析 @putint 和 @putstr 函数符号
        Function putintFunc = (Function) scopeManager.resolve("putint").getIrValue();
        Function putstrFunc = (Function) scopeManager.resolve("putstr").getIrValue();
        //
        // // 2. 获取格式字符串，例如 "Hello %d world\n"
        String formatString = stmt.getFormatString().getContent();
        formatString = formatString.substring(1, formatString.length() - 1); // 去掉引号
        // // 3. 递归访问所有表达式参数，存起来
        // //    例如: (a, b+1) -> [%v5, %v6]
        ArrayList<Value> args = new ArrayList<>();
        for (Exp exp : stmt.getExps()) {
            args.add(visitExp(exp));
        }
        int argIndex = 0; // 参数索引
        // 4. *关键*：循环拆解字符串
        //    (使用 String.split("%d", -1) 是一种巧妙的方法)
        //    例如: "Hello %d world\n%d" -> ["Hello ", " world\n", ""]
        String[] parts = formatString.split("%d", -1);
        //
        for (int i = 0;i < parts.length; i++) {
            String part = parts[i];
            if (!part.isEmpty()) {
                // 5a. 获取或创建 ConstString
                ConstString constString = module.getOrAddConstString(part);
                // 5b. 创建 GEP 获取 i8* 指针
                //    例如: "%ptr.5 = gep [7 x i8]*, @.str.0, i32 0, i32 0"
                Value idx0 = ConstInt.get(IntegerType.get32(),0);
                String gepName = nameManager.newVarName();
                GepInst gep = new GepInst(gepName, constString, new ArrayList<>(java.util.List.of(idx0, idx0)));
                state.getCurrentBlock().addInstruction(gep);
                // 5c. 创建 PutstrInst
                PutstrInst putstrInst = new PutstrInst(putstrFunc, gep);
                state.getCurrentBlock().addInstruction(putstrInst);
            }
            // 6. 处理 %d (如果它不是最后一个 part)
            if (i < parts.length - 1) {
                Value arg = args.get(argIndex++);
                // 6b. 创建 PutintInst
                //    例如: "call void @putint(i32 %v5)"
                PutintInst putintInst = new PutintInst(putintFunc,arg);
                state.getCurrentBlock().addInstruction(putintInst);
            }
        }
    }

    /**
     * (新) 访问一个表达式语句 (ExpStmt)
     * (对应 原始代码 buildStmt 的 ExpStmt 分支)
     */
    private void visitExpStmt(ExpStmt stmt) {
        // // 提示：
        // // 1. 检查是否有表达式 (例如空语句 ";")
        if (stmt.getExp() != null) {
            // 2. 递归访问表达式
            //    这会生成表达式中的所有 IR，
            //    例如，如果语句是 "myFunc(a);"
            //    visitExp 会生成 call 指令。
            //    我们不需要使用 visitExp 的返回值。
            visitExp(stmt.getExp());
        }
    }

    /**
     * (新) 访问一个返回语句 (ReturnStmt)
     * (对应 原始代码 buildReturnStmt)
     */
    private void visitReturnStmt(ReturnStmt stmt) {
        // // 提示：
        // // 1. 获取当前函数，检查返回类型
        Function func = state.getCurrentFunction();
        if (func.getReturnType() instanceof  VoidType) {
            // --- 情况 1：void 函数 (return;) ---
            RetInst retInst = new RetInst();
            state.getCurrentBlock().addInstruction(retInst);
        } else {
            // --- 情况 2：i32 函数 (return exp;) ---
            // a. 递归访问表达式，获取返回值
            //    例如: (a + 1) -> %v7
            Value retValue = visitExp(stmt.getExp());
            // b. 创建 ret 指令
            //    例如: "ret i32 %v7"
            RetInst retInst = new RetInst(retValue);
            state.getCurrentBlock().addInstruction(retInst);
        }
    }

    /**
     * (修正) 访问一个 If 语句 (IfStmt)
     * *** 修正了 BrInst，并添加了可达性逻辑 ***
     */
    private void visitIfStmt(IfStmt stmt) {
        // 1. 获取当前函数
        Function func = state.getCurrentFunction();
        boolean hasElse = (stmt.getElseStmt() != null);

        // 2. *立即* 创建所有需要的块。这更简单。
        BasicBlock trueBlock = new BasicBlock(nameManager.newBlockName("if.then"), func);
        BasicBlock falseBlock = null; // 只有在 hasElse 时才创建
        BasicBlock followBlock = new BasicBlock(nameManager.newBlockName("if.follow"), func);

        // 3. 决定 false 分支的目标
        BasicBlock falseTarget;
        if (hasElse) {
            falseBlock = new BasicBlock(nameManager.newBlockName("if.else"), func);
            falseTarget = falseBlock; // (有 else, 跳转到 else 块)
        } else {
            falseTarget = followBlock; // (无 else, 直接跳转到 follow 块)
        }

        // 4. 生成条件跳转
        visitCond(stmt.getCond(), trueBlock, falseTarget);

        // 5. 填充 True 块
        state.setCurrentBlock(trueBlock);
        visitStmt(stmt.getIfStmt());

        // 6. *修正*：获取 true 分支 *结束* 时的块
        BasicBlock trueEndBlock = state.getCurrentBlock();
        // 7. 如果 true 分支没有被 "ret" 终结，就添加 "br"
        if (trueEndBlock.getTerminator() == null) {
            trueEndBlock.addInstruction(new BrInst(followBlock));
        }

        // 8. 填充 False 块
        BasicBlock falseEndBlock = null;
        if (hasElse) {
            state.setCurrentBlock(falseBlock);
            visitStmt(stmt.getElseStmt());

            // 9. *修正*：获取 false 分支 *结束* 时的块
            falseEndBlock = state.getCurrentBlock();
            // 10. 如果 false 分支没有被 "ret" 终结，就添加 "br"
            if (falseEndBlock.getTerminator() == null) {
                falseEndBlock.addInstruction(new BrInst(followBlock));
            }
        }

        // 11. 决定 follow 块是否可达
        boolean trueTerminated = (trueEndBlock.getTerminator() != null);
        boolean falseTerminated = (hasElse) ? (falseEndBlock.getTerminator() != null) : false; // (无 else 就算未终结)

        if (trueTerminated && (hasElse && falseTerminated)) {
            // A. (情况 A: if {ret} else {ret})
            // follow 块是不可达的 "死代码"。
            // 我们可以删除它（可选），但 *不能* 将它设为当前块。
            // （为安全起见，我们暂时不删除它，但也不设置它）
            // (注意：这可能导致后续代码生成在错误的块中，
            //  一个更安全的做法是 *总是* 设置 followBlock 为当前块)
            state.setCurrentBlock(followBlock);
        } else {
            // B. (情况 B: 至少一个分支会到达 follow)
            // 这是正常情况，将 follow 块设为当前块
            state.setCurrentBlock(followBlock);
        }
    }

    /**
     * (修正) 访问一个 For 语句 (ForLoopStmt)
     * *** 修正了所有 BrInst ***
     */
    private void visitForStmt(ForLoopStmt stmt) {
        // 1. 获取当前函数
        Function func = state.getCurrentFunction();
        // 2. 创建所有需要的块
        BasicBlock condBlock = new BasicBlock(nameManager.newBlockName("for.cond"), func);
        BasicBlock bodyBlock = new BasicBlock(nameManager.newBlockName("for.body"), func);
        BasicBlock updateBlock = new BasicBlock(nameManager.newBlockName("for.update"), func);
        BasicBlock followBlock = new BasicBlock(nameManager.newBlockName("for.follow"), func);

        // 3. *关键*：将循环信息压入栈 (用于 break/continue)
        state.pushLoop(updateBlock, followBlock);

        // 3. *修正*：处理 ForStmt1 (初始化)
        if (stmt.getInit() != null) {
            ForStmt forInit = stmt.getInit();
            for (int i = 0; i < forInit.getLVals().size(); i++) {
                buildAssignment(forInit.getLVals().get(i), forInit.getExps().get(i));
            }
        }

        // 4. 从当前块跳转到 Cond 块
        // --- 修正：必须显式 addInstruction ---
        BrInst brToCond = new BrInst(condBlock);
        state.getCurrentBlock().addInstruction(brToCond);

        // 5. 填充 Cond 块
        state.setCurrentBlock(condBlock);
        if (stmt.getCond() != null) {
            visitCond(stmt.getCond(), bodyBlock, followBlock);
        } else {
            // --- 修正：必须显式 addInstruction ---
            BrInst brToBody = new BrInst(bodyBlock); // (无条件)
            state.getCurrentBlock().addInstruction(brToBody);
        }

        // 6. 填充 Body 块
        state.setCurrentBlock(bodyBlock);
        visitStmt(stmt.getLoopBody());
        if (state.getCurrentBlock().getTerminator() == null) {
            BrInst brToUpdate = new BrInst(updateBlock); // (跳转到 Update)
            state.getCurrentBlock().addInstruction(brToUpdate);
        }

        // 7. 填充 Update 块
        state.setCurrentBlock(updateBlock);
        if (stmt.getUpdate() != null) {
            ForStmt forUpdate = stmt.getUpdate();
            for (int i = 0; i < forUpdate.getLVals().size(); i++) {
                buildAssignment(forUpdate.getLVals().get(i), forUpdate.getExps().get(i));
            }
        }
        if (state.getCurrentBlock().getTerminator() == null) {
            BrInst brToCond2 = new BrInst(condBlock); // 必须跳转回 condBlock
            state.getCurrentBlock().addInstruction(brToCond2);
        }

        // 8. *关键*：弹出循环栈
        state.popLoop();

        // 9. 将当前块设置为 follow 块
        state.setCurrentBlock(followBlock);
    }

    /**
     * (修正) 访问 Break 语句
     * *** 修正了 BrInst ***
     */
    private void visitBreakStmt(BreakStmt stmt) {
        // 1. 从循环栈获取 "break" 目标块
        BasicBlock breakTarget = state.getBreakTarget();

        // 2. 创建无条件跳转
        // --- 修正：必须显式 addInstruction ---
        BrInst br = new BrInst(breakTarget);
        state.getCurrentBlock().addInstruction(br);
    }

    /**
     * (修正) 访问 Continue 语句
     * *** 修正了 BrInst ***
     */
    private void visitContinueStmt(ContinueStmt stmt) {
        // 1. 从循环栈获取 "continue" 目标块
        BasicBlock continueTarget = state.getContinueTarget();

        // 2. 创建无条件跳转
        // --- 修正：必须显式 addInstruction ---
        BrInst br = new BrInst(continueTarget);
        state.getCurrentBlock().addInstruction(br);
    }

    /**
     * (新) 访问一个条件 (Cond)
     * * 关键*：生成短路求值的控制流
     * (对应 原始代码 buildCond)
     *
     * @param cond AST 节点
     * @param trueBlock 如果条件为 true，跳转到这里
     * @param falseBlock 如果条件为 false，跳转到这里
     */
    private void visitCond(Cond cond, BasicBlock trueBlock, BasicBlock falseBlock) {
        // // Cond 的根是 LOrExp
        ArrayList<LAndExp> lAndExps = cond.getLOrExp().getLAndExps();
        // // (短路求值：a || b || c)
        for (int i = 0;i < lAndExps.size();i++) {
            LAndExp lAndExp = lAndExps.get(i);
            if (i == lAndExps.size() - 1) {
                // --- 1. 这是最后一个条件 (c) ---
                // (如果 c 为 true, 跳到 trueBlock)
                // (如果 c 为 false, 跳到 falseBlock)
                visitLAndExp(lAndExp,trueBlock,falseBlock);
            } else {
                // --- 2. 这不是最后一个条件 (a 或 b) ---
                // (创建一个新的块，用于下一个 LAndExp)
                // (例如: "check.b")
                BasicBlock nextLAndBlock = new BasicBlock(nameManager.newBlockName("lor.rhs"),state.getCurrentFunction());
                // (如果 a 为 true, 跳到 trueBlock)
                // (如果 a 为 false, *不要* 跳到 falseBlock，而是跳到 nextLAndBlock)
                visitLAndExp(lAndExp,trueBlock,nextLAndBlock);
                // (更新当前块，为下一个循环做准备)
                state.setCurrentBlock(nextLAndBlock);
            }
        }
    }

    /**
     * (新) 访问一个 LAndExp (&&)
     * (对应 原始代码 buildLAndExp)
     */
    private void visitLAndExp(LAndExp lAndExp, BasicBlock trueBlock, BasicBlock falseBlock) {
        ArrayList<EqExp> eqExps = lAndExp.getEqExps();
        // // (短路求值：a && b && c)
        for (int i = 0; i < eqExps.size(); i++) {
            EqExp eqExp = eqExps.get(i);
            if (i == eqExps.size() - 1) {
                // --- 1. 这是最后一个条件 (c) ---
                // (如果 c 为 true, 跳到 trueBlock)
                // (如果 c 为 false, 跳到 falseBlock)
                buildCondBranch(eqExp, trueBlock, falseBlock);
            } else {
                // --- 2. 这不是最后一个条件 (a 或 b) ---
                // (创建一个新的块，用于下一个 EqExp)
                // (例如: "check.b")
                BasicBlock nextEqBlock = new BasicBlock(nameManager.newBlockName("land.rhs"), state.getCurrentFunction());
                // (如果 a 为 true, *不要* 跳到 trueBlock，而是跳到 nextEqBlock)
                // (如果 a 为 false, 跳到 falseBlock)
                buildCondBranch(eqExp, nextEqBlock, falseBlock);
                // (更新当前块，为下一个循环做准备)
                state.setCurrentBlock(nextEqBlock);
            }
        }
    }

    /**
     * (新) 辅助方法：生成条件的 BrInst
     * (对应 原始代码 buildLAndExp 的最后几行)
     */
    private void buildCondBranch(EqExp eqExp, BasicBlock trueBlock, BasicBlock falseBlock) {
        // // 提示：
        // // 1. 递归访问 EqExp，获取一个 i32 (0 或 1) 或 i1 (bool) 的 Value
        // //    (例如 a > b -> %v10 (i1))
        Value condition = visitEqExp(eqExp);
        // // 2. *关键*：将条件转换为 i1 (bool)
        // //    (visitEqExp 可能返回 i32 或 i1)
        Value i1Condition;
        if (condition.getType() == IntegerType.get32()) {
            // a. (情况 1: 是 i32)
            //    我们需要 "icmp ne i32 %v, 0"
            String name = nameManager.newVarName();
            Constant zero = ConstInt.get(IntegerType.get32(),0);
            BinaryInst inst = new BinaryInst(BinaryOpCode.NE, condition, zero);
            inst.setName(name);
            state.getCurrentBlock().addInstruction(inst);
            i1Condition = inst;
        } else {
            // b. (情况 2: 已经是 i1)
            i1Condition = condition;
        }
        // // 3. 创建 *条件分支* 指令
        // //    例如: "br i1 %v10, label %if.then, label %if.else"
        BrInst br = new BrInst(i1Condition, trueBlock, falseBlock);
        state.getCurrentBlock().addInstruction(br);
    }

    /**
     * (新) 访问一个 EqExp (==, !=)
     * (对应 原始代码 buildEqExp)
     * @return 一个 i32 (如果是 RelExp) 或 i1 (如果是比较) 的 Value
     */
    private Value visitEqExp(EqExp eqExp) {
        // 1. 访问第一个 RelExp
        Value left = visitRelExp(eqExp.getRelExps().get(0));

        // 2. (关键) 如果只有一个 RelExp
        if (eqExp.getRelExps().size() == 1) {
            return left; // (返回 i32 或 i1)
        }

        // 3. 循环处理 (a == b != c ...)
        for (int i = 1; i < eqExp.getRelExps().size(); i++) {
            TokenType op = eqExp.getOperators().get(i - 1).getType();
            Value right = visitRelExp(eqExp.getRelExps().get(i));

            // --- 【【 必 须 添 加 的 修 正 】】 ---
            // 在比较 i32 和 i1 之前，必须将 i1 提升为 i32

            Type i32Type = IntegerType.get32();
            Type i1Type = IntegerType.get1();

            if (left.getType() == i32Type && right.getType() == i1Type) {
                // 情况: i32 == i1  (例如 1 == (a > 3))
                // 提升 right
                String zextName = nameManager.newVarName();
                ZextInst zext = new ZextInst(zextName, right, i32Type);
                state.getCurrentBlock().addInstruction(zext);
                right = zext; // (现在 right 也是 i32 了)

            } else if (left.getType() == i1Type && right.getType() == i32Type) {
                // 情况: i1 == i32  (例如 (a > 3) == 1)
                // 提升 left
                String zextName = nameManager.newVarName();
                ZextInst zext = new ZextInst(zextName, left, i32Type);
                state.getCurrentBlock().addInstruction(zext);
                left = zext; // (现在 left 也是 i32 了)
            }
            // (如果两者都是 i32，或两者都是 i1，我们不需要做任何事)
            // --- 【 修 正 结 束 】 ---


            // 4. 确定 OpCode (EQ 或 NE)
            BinaryOpCode opCode = (op == TokenType.EQL) ? BinaryOpCode.EQ : BinaryOpCode.NE;

            // 5. 创建 BinaryInst (icmp)
            //    (现在 left 和 right 的类型保证是匹配的)
            String name = nameManager.newVarName();
            BinaryInst inst = new BinaryInst(opCode, left, right);
            inst.setName(name);
            state.getCurrentBlock().addInstruction(inst);

            // 6. 更新 left (现在是 i1)
            left = inst;
        }

        return left; // (返回 i1)
    }

    /**
     * (新) 访问一个 RelExp (<, >, <=, >=)
     * (对应 原始代码 buildRelExp)
     * @return 一个 i32 (如果是 AddExp) 或 i1 (如果是比较) 的 Value
     */
    private Value visitRelExp(RelExp relExp) {
         // 提示：
         // 1. 访问第一个 AddExp
         Value left = visitAddExp(relExp.getAddExps().get(0));

         // 2. (关键) 如果只有一个 AddExp，*不要* 进行比较
         if (relExp.getAddExps().size() == 1) {
             return left; // (返回 i32)
         }

         // 3. 循环处理 (a > b < c ...)
         for (int i = 1; i < relExp.getAddExps().size(); i++) {
             TokenType op = relExp.getOperators().get(i - 1).getType();
             Value right = visitAddExp(relExp.getAddExps().get(i));

             // 4. 确定 OpCode
             BinaryOpCode opCode;
             if (op == TokenType.LSS) {
                 opCode = BinaryOpCode.SLT;
             } else if (op == TokenType.LEQ) {
                 opCode = BinaryOpCode.SLE;
             } else if (op == TokenType.GRE) {
                 opCode = BinaryOpCode.SGT;
             } else { // (op == TokenType.GEQ)
                 opCode = BinaryOpCode.SGE;
             }

             // 5. 创建 BinaryInst (icmp)
             //    例如: "%v9 = icmp sgt i32 %a, %b"
             String name = nameManager.newVarName();
             BinaryInst inst = new BinaryInst(opCode, left, right);
             inst.setName(name);
             state.getCurrentBlock().addInstruction(inst);

             // 6. 更新 left (现在是 i1)
             left = inst;
         }

         return left; // (返回 i1)
    }
}
