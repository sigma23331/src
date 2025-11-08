package middle;

import error.ErrorHandler;
import frontend.Token.TokenType;
import frontend.syntax.BType;
import frontend.syntax.Block;
import frontend.syntax.BlockItem;
import frontend.syntax.CompileUnit;
import frontend.syntax.expression.AddExp;
import frontend.syntax.expression.Exp;
import frontend.syntax.expression.MulExp;
import frontend.syntax.expression.UnaryExp;
import frontend.syntax.function.FuncDef;
import frontend.syntax.function.FuncFParam;
import frontend.syntax.function.MainFuncDef;
import frontend.syntax.statement.*;
import frontend.syntax.variable.*;
import frontend.syntax.expression.*;
import middle.component.type.*;
import middle.component.InitialValue;
import middle.symbol.FunctionSymbol;
import middle.symbol.VarSymbol;

import java.util.ArrayList;
import java.util.List;

/**
 * 语义分析第一遍：符号收集器 (The "Registrar")
 * <p>
 * 职责：
 * 1. 遍历AST，【构建一个持久的作用域树】。
 * 2. 将所有声明注册到对应的作用域节点中。
 * 3. 【装饰AST】：将创建的 Scope 对象关联到对应的 AST 节点上。
 */
public class SymbolCollector {

    private final ScopeManager scopeManager;
    private final ErrorHandler errorHandler;
    private final ConstCalculater constCalculator; // 用于计算常量的值

    private boolean isGlobal = true;

    public SymbolCollector(ScopeManager scopeManager, ErrorHandler errorHandler) {
        this.scopeManager = scopeManager;
        this.errorHandler = errorHandler;
        this.constCalculator = new ConstCalculater(this.scopeManager, this.errorHandler);
    }

    public void visit(CompileUnit compUnit) {
        // 1. 【修改】进入全局作用域并获取 Scope 对象
        Scope globalScope = scopeManager.enterScope();
        // 假设 CompileUnit 也可以被装饰以存储全局作用域，如果需要的话
        // compUnit.setScope(globalScope);

        // 2. 遍历所有全局声明
        for (Decl decl : compUnit.getDecls()) {
            visitDecl(decl);
        }

        // 3. 遍历所有函数定义
        this.isGlobal = false;
        for (FuncDef funcDef : compUnit.getFuncDefs()) {
            visitFuncDef(funcDef);
        }

        // 4. 访问 main 函数
        visitMainFuncDef(compUnit.getMainFuncDef());

        // 5. 【修改】第一遍结束后，将 ScopeManager 的指针重置回根节点的父节点 (null)
        // 这样可以确保 ScopeManager 处于一个干净的初始状态，为第二遍做准备
        scopeManager.exitScope();
    }

    private void visitDecl(Decl decl) {
        if (decl instanceof ConstDecl) {
            visitConstDecl((ConstDecl) decl);
        } else if (decl instanceof VarDecl) {
            visitVarDecl((VarDecl) decl);
        }
    }

    private void visitConstDecl(ConstDecl constDecl) {
        for (ConstDef constDef : constDecl.getConstDefs()) {
            visitConstDef(constDef, constDecl.getBType());
        }
    }

    private void visitVarDecl(VarDecl varDecl) {
        for (VarDef varDef : varDecl.getVarDefs()) {
            // 将 isStatic 标志传递下去
            visitVarDef(varDef, varDecl.getBType(), varDecl.isStatic());
        }
    }

    private void visitConstDef(ConstDef constDef, BType bType) {
        Type baseType = parseBType(bType);
        Type symbolType = baseType;
        int length = 0;
        if (constDef.getArraySize() != null) {
            length = constCalculator.calculate(constDef.getArraySize());
            symbolType = new ArrayType(baseType, length);
        }
        ArrayList<Integer> values = constCalculator.calculateInitVal(constDef.getConstInitVal());
        InitialValue initialValue = new InitialValue(symbolType, length, values);

        // 全局 const 具有静态存储周期，局部 const 具有自动存储周期
        boolean storageIsStatic = isGlobal;

        VarSymbol varSymbol = new VarSymbol(
                constDef.getIdent().getContent(),
                symbolType,
                true,           // isConstant
                storageIsStatic,  // isStatic
                initialValue,
                constDef.getIdent().getLine());
        scopeManager.define(varSymbol, constDef.getIdent().getLine());
    }

    private void visitVarDef(VarDef varDef, BType bType, boolean hasStaticKeyword) {
        Type baseType = parseBType(bType);
        Type symbolType = baseType;
        int length = 0;
        if (varDef.getArraySize() != null) {
            length = constCalculator.calculate(varDef.getArraySize());
            symbolType = new ArrayType(baseType, length);
        }

        boolean storageIsStatic;
        InitialValue initialValue = null;

        boolean needsCompileTimeInit = isGlobal || hasStaticKeyword;

        if (isGlobal) {
            storageIsStatic = true;
        } else {
            storageIsStatic = hasStaticKeyword;
        }

        // 现在，我们为所有带初始化的变量都尝试计算其初始值
        if (varDef.getInitVal() != null) {
            // ConstCalculator 现在是“编译时初始值计算器”
            // 它能处理 const int a = 5; 和 int a = 10;
            ArrayList<Integer> values = constCalculator.calculateInitVal(varDef.getInitVal());
            initialValue = new InitialValue(symbolType, length, values);
        } else {
            // 如果没有初始化表达式，我们仍然为全局和静态变量创建默认的 InitialValue
            if (needsCompileTimeInit) {
                initialValue = new InitialValue(symbolType, length, null); // null list -> 默认为0
            }
        }

        VarSymbol varSymbol = new VarSymbol(
                varDef.getIdent().getContent(),
                symbolType,
                false,          // isConstant
                hasStaticKeyword,  // 可能之后需要改
                initialValue,
                varDef.getIdent().getLine());
        scopeManager.define(varSymbol, varDef.getIdent().getLine());
    }

    private void visitFuncDef(FuncDef funcDef) {
        Type returnType = (funcDef.getFuncType().getType() == TokenType.VOIDTK)
                ? VoidType.getInstance() : IntegerType.get(32);
        List<Type> paramTypes = new ArrayList<>();
        List<String> paramNames = new ArrayList<>();
        if (funcDef.getFuncFParams() != null) {
            for (FuncFParam param : funcDef.getFuncFParams().getParams()) {
                Type baseType = parseBType(param.getBType());
                Type paramType = (param.isArray()) ? new ArrayType(baseType, -1) : baseType;
                paramTypes.add(paramType);
                paramNames.add(param.getIdent().getContent());
            }
        }
        FunctionType funcType = new FunctionType(returnType, paramTypes);
        FunctionSymbol funcSymbol = new FunctionSymbol(
                funcDef.getIdent().getContent(), funcType, paramNames, funcDef.getIdent().getLine());

        // 在父作用域定义函数
        scopeManager.define(funcSymbol, funcDef.getIdent().getLine());

        // 【修改】进入函数的新作用域，并获取 Scope 对象
        Scope funcScope = scopeManager.enterScope();

        // 【新增】将 Scope 对象“挂”在 AST 节点上
        funcDef.setScope(funcScope);

        // 在新作用域中定义参数变量
        if (funcDef.getFuncFParams() != null) {
            List<FuncFParam> paramNodes = funcDef.getFuncFParams().getParams();
            for (int i = 0; i < paramNames.size(); i++) {
                VarSymbol paramVar = new VarSymbol(
                        paramNames.get(i), paramTypes.get(i), false, false, null,paramNodes.get(i).getIdent().getLine());
                scopeManager.define(paramVar, paramNodes.get(i).getIdent().getLine());
            }
        }

        // 访问函数体
        visitBlock(funcDef.getBlock());

        // 退出函数作用域（移动指针，不丢失信息）
        scopeManager.exitScope();
    }

    private void visitMainFuncDef(MainFuncDef mainFuncDef) {
        Type returnType = IntegerType.get(32);
        FunctionType funcType = new FunctionType(returnType, new ArrayList<>());
        FunctionSymbol funcSymbol = new FunctionSymbol(
                "main", funcType, new ArrayList<>(), 0); // 应该不会被调用

        // 在全局定义 main 函数
        scopeManager.define(funcSymbol, mainFuncDef.getbType().getLine());

        // 【修改】进入 main 的作用域，并获取 Scope 对象
        Scope mainFuncScope = scopeManager.enterScope();

        // 【新增】装饰 AST 节点
        mainFuncDef.setScope(mainFuncScope);

        // 访问函数体
        visitBlock(mainFuncDef.getBlock());

        // 退出 main 的作用域
        scopeManager.exitScope();
    }

    private void visitBlock(Block block) {
        for (BlockItem item : block.getBlockItems()) {
            if (item instanceof Decl) {
                visitDecl((Decl) item);
            } else if (item instanceof Stmt) {
                visitStmt((Stmt) item);
            }
        }
    }

    private void visitStmt(Stmt stmt) {
        if (stmt instanceof BlockStmt) {
            visitBlockStmt((BlockStmt) stmt);
        }
        else if (stmt instanceof IfStmt) {
            visitIfStmt((IfStmt) stmt);
        } else if (stmt instanceof ForLoopStmt) {
            visitForStruct((ForLoopStmt) stmt);
        }
        // 在第一遍中，我们不关心其他只包含表达式的语句
    }

    private void visitBlockStmt(BlockStmt blockStmt) {
        // 【修改】进入新作用域并获取 Scope 对象
        Scope blockScope = scopeManager.enterScope();

        // 【新增】装饰 AST 节点
        blockStmt.setScope(blockScope);

        visitBlock(blockStmt.getBlock());

        scopeManager.exitScope();
    }

    private void visitIfStmt(IfStmt ifStmt) {
        // 只递归访问可能包含声明的语句块
        visitStmt(ifStmt.getIfStmt());
        if (ifStmt.getElseStmt() != null) {
            visitStmt(ifStmt.getElseStmt());
        }
    }

    private void visitForStruct(ForLoopStmt forStruct) {
        // 只递归访问可能包含声明的循环体
        visitStmt(forStruct.getLoopBody());
    }

    private Type parseBType(BType bType) {
        if (bType.getToken().getType() == TokenType.INTTK) {
            return IntegerType.get(32);
        } else {
            // 假设 SysY 的 char 也可以用整数类型表示
            return IntegerType.get(32); // 或者 CharType.getInstance()
        }
    }
}
