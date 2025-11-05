package middle;
import java.util.HashMap;
import java.util.Stack;
import error.ErrorHandler; // 假设你有错误处理器
import error.Error;
import error.ErrorType;
import middle.component.type.ArrayType;
import middle.component.type.Type;
import middle.component.type.VoidType;
import middle.symbol.FunctionSymbol;
import middle.symbol.Symbol;
import middle.symbol.SymbolRecord;
import middle.symbol.VarSymbol;

public class ScopeManager {
    private final ErrorHandler errorHandler;
    private final SymbolLogger logger = SymbolLogger.getInstance();

    private Scope globalScope;      // 树的根节点
    private Scope currentScope;     // 指向当前作用域的【指针】
    private int nextScopeId = 1;  // 用于分配ID



    public ScopeManager(ErrorHandler errorHandler) {
        this.errorHandler = errorHandler;
    }

    // --- 【第一遍】使用的方法 ---

    /**
     * 【第一遍调用】进入一个新作用域，并【创建】树节点。
     * @return 返回新创建的 Scope 对象，以便“挂”在AST节点上。
     */
    public Scope enterScope() {
        Scope newScope = new Scope(nextScopeId++, currentScope);
        if (currentScope != null) {
            currentScope.addChild(newScope);
        } else {
            globalScope = newScope; // 第一个作用域是全局作用域
        }
        currentScope = newScope;
        return newScope;
    }

    /**
     * 【第一遍调用】在【当前】作用域定义一个新符号。
     */
    public void define(Symbol symbol, int lineNumber) {
        if (currentScope == null) return; // 应该先调用 enterScope

        // 检查【当前作用域】是否已重定义
        if (currentScope.lookupLocally(symbol.getName()) != null) {
            errorHandler.addError(ErrorType.RedefineIdent, lineNumber);
        } else {
            currentScope.addSymbol(symbol);

            // 通知 Logger
            if (!symbol.getName().equals("main")) {
                String typeName = formatTypeForOutput(symbol);
                logger.record(new SymbolRecord(currentScope.getId(), symbol.getName(), typeName));
            }
        }
    }

    // --- 【第一遍 & 第二遍】都使用的方法 ---

    /**
     * 退出当前作用域（实际是移动指针回到父节点）。
     */
    public void exitScope() {
        if (currentScope != null) {
            currentScope = currentScope.getParent();
        }
    }

    // --- 【第二遍】使用的方法 ---

    /**
     * 【第二遍调用】从【当前】开始，向【外层】查找一个符号。
     */
    public Symbol resolve(String name) {
        Scope scope = currentScope;
        while (scope != null) {
            Symbol symbol = scope.lookupLocally(name);
            if (symbol != null) {
                return symbol;
            }
            scope = scope.getParent(); // 向上查找
        }
        return null; // 所有作用域都找不到
    }

    public Symbol resolve(String name, int usageLine) {
        Scope scope = currentScope;
        while (scope != null) {
            Symbol symbol = scope.lookupLocally(name);

            if (symbol != null) {
                // 找到了

                // 如果在全局作用域，或者
                // 如果在局部作用域 且 声明行号 < 使用行号
                if (symbol.getDefineLine() < usageLine) {
                    return symbol; // 合法使用 (定义在前面)
                }

            }
            scope = scope.getParent(); // 去父作用域
        }
        return null; // 查遍所有层都没找到
    }

    /**
     * 【第二遍调用】将指针直接【跳转】到一个已存在的作用域。
     * @param scope 第一遍为这个AST节点创建的 Scope 对象。
     */
    public void setCurrentScope(Scope scope) {
        this.currentScope = scope;
    }

    // --- 其他辅助方法 ---
    private String formatTypeForOutput(Symbol symbol) {
        if (symbol instanceof FunctionSymbol) {
            FunctionSymbol fs = (FunctionSymbol) symbol;
            Type returnType = fs.getType().getReturnType();
            if (returnType instanceof VoidType) {
                return "VoidFunc";
            } else {
                return "IntFunc";
            }
        }

        if (symbol instanceof VarSymbol) {
            VarSymbol vs = (VarSymbol) symbol;
            boolean isConst = vs.isConstant();
            boolean isStatic = vs.isStatic();
            boolean isArray = vs.getType() instanceof ArrayType;

            if (isConst) {
                return isArray ? "ConstIntArray" : "ConstInt";
            }
            if (isStatic) {
                return isArray ? "StaticIntArray" : "StaticInt";
            }
            return isArray ? "IntArray" : "Int";
        }

        return "Unknown";
    }

    public Scope getGlobalScope() {
        return globalScope;
    }
}
