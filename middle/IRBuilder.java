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
            // (如果没有初始值，我们只创建 alloca，不 store)
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
     * (对应 原始代码 buildLValValue)
     */
    private Value visitLValValue(LVal lVal) {
        // 1. 解析 LVal 符号
        VarSymbol symbol = (VarSymbol) scopeManager.resolve(lVal.getIdent().getContent());

        // 2. 获取该变量的 *地址* (指针)
        //    (在 visitLocalDecl 中，我们用 setIrValue 将符号链接到了 AllocInst)
        Value pointer = symbol.getIrValue(); // (例如 %a.addr.0)

        if (symbol.getType() instanceof ArrayType) {
            // --- 情况 2：数组访问 (例如 `a[i]`) ---

            // a. 递归访问索引表达式
            //    例如: (i) -> %v7
            Value index = visitExp(lVal.getIndex());

            // b. 创建 GEP 指令
            //    例如: "%ptr.3 = gep [10 x i32]*, %arr.addr, i32 0, i32 %v7"
            Value idx0 = ConstInt.get(IntegerType.get32(), 0);
            String gepName = nameManager.newVarName();
            GepInst gep = new GepInst(gepName, pointer, new ArrayList<>(java.util.List.of(idx0, index)));
            state.getCurrentBlock().addInstruction(gep);

            // c. 创建 Load 指令
            //    例如: "%v8 = load i32, i32* %ptr.3"
            String loadName = nameManager.newVarName();
            LoadInst load = new LoadInst(loadName, gep);
            state.getCurrentBlock().addInstruction(load);
            return load;

        } else {
            // --- 情况 1：标量变量 (例如 `a`) ---

            // a. 创建 Load 指令
            //    例如: "%v6 = load i32, i32* %a.addr.0"
            String name = nameManager.newVarName();
            LoadInst load = new LoadInst(name, pointer);
            state.getCurrentBlock().addInstruction(load);
            return load;
        }
    }

    /**
     * (已实现) 访问 LVal (作为 *赋值* 目标)
     * * 关键*：*不* 生成 Load，只返回地址
     * (对应 原始代码 buildLValAssign)
     */
    private Value visitLValAssign(LVal lVal) {
        // 1. 解析 LVal 符号
        VarSymbol symbol = (VarSymbol) scopeManager.resolve(lVal.getIdent().getContent());

        // 2. 获取该变量的 *基地址* (指针)
        Value basePointer = symbol.getIrValue(); // (例如 %a.addr.0 或 %arr.addr)

        if (symbol.getType() instanceof ArrayType) {
            // --- 情况 2：数组 (例如 a[i] = ...) ---

            // a. 递归访问索引表达式
            Value index = visitExp(lVal.getIndex());

            // b. 创建 GEP 指令
            //    例如: "%ptr.4 = gep [10 x i32]*, %arr.addr, i32 0, i32 %v7"
            Value idx0 = ConstInt.get(IntegerType.get32(), 0);
            String gepName = nameManager.newVarName();
            GepInst gep = new GepInst(gepName, basePointer, new ArrayList<>(java.util.List.of(idx0, index)));
            state.getCurrentBlock().addInstruction(gep);

            // c. 返回 GEP 的结果 (地址)
            return gep; // (例如 %ptr.4)

        } else {
            // --- 情况 1：标量 (例如 a = ...) ---
            //    直接返回 alloca 的地址
            return basePointer; // (例如 %a.addr.0)
        }
    }
}
