package middle;

import frontend.Token.TokenType;
import frontend.syntax.*;
import frontend.syntax.expression.*;
import frontend.syntax.variable.ConstInitVal;
import frontend.syntax.variable.InitVal;
import middle.component.InitialValue;
import middle.symbol.*;
import error.ErrorHandler;
import error.Error;
import error.ErrorType;

import java.util.ArrayList;

/**
 * 辅助类：编译时初始值求值器
 * 职责：计算任何可在编译时确定的初始值，
 * 包括常量和依赖于其他具有编译时初始值的变量。
 */
public class ConstCalculater {

    private final ScopeManager scopeManager;
    private final ErrorHandler errorHandler;

    public ConstCalculater(ScopeManager scopeManager, ErrorHandler errorHandler) {
        this.scopeManager = scopeManager;
        this.errorHandler = errorHandler;
    }

    // 主入口 (由 SymbolCollector 调用)
    public int calculate(ConstExp constExp) {
        if (constExp == null) return 0;
        return visitAddExp(constExp.getAddExp());
    }

    // 内部递归使用的辅助方法
    private int calculate(Exp exp) {
        if (exp == null) return 0;
        // 假设 Exp 的顶层节点是 AddExp
        return visitAddExp(exp.getAddExp());
    }

    private int visitAddExp(AddExp addExp) {
        if (addExp == null) return 0;
        int res = visitMulExp((addExp.getMulExps().get(0)));
        for (int i = 1;i < addExp.getMulExps().size();i++) {
            TokenType op = addExp.getOperators().get(i - 1).getType();
            int rhs = visitMulExp(addExp.getMulExps().get(i));
            if (op == TokenType.PLUS) res += rhs;
            else res -= rhs;
        }
        return res;
    }

    private int visitMulExp(MulExp mulExp) {
        if (mulExp == null) return 0;
        int res = visitUnaryExp((mulExp.getUnaryExps().get(0)));
        for (int i = 1; i < mulExp.getUnaryExps().size(); i++) {
            TokenType op = mulExp.getOperators().get(i - 1).getType();
            int rhs = visitUnaryExp(mulExp.getUnaryExps().get(i));
            if (op == TokenType.MULT) res *= rhs;
            else if (op== TokenType.DIV) {
                if (rhs == 0) { /* 错误：除以零 */ return 0; }
                res /= rhs;
            } else {
                if (rhs == 0) { /* 错误：对零取模 */ return 0; }
                res %= rhs;
            }
        }
        return res;
    }

    private int visitUnaryExp(UnaryExp unaryExp) {
        if (unaryExp == null) return 0;
        if (unaryExp.getPrimaryExp() != null) {
            PrimaryExp primaryExp = unaryExp.getPrimaryExp();
            if (primaryExp.getExp() != null) {
                // 递归调用 calculate(Exp)
                return calculate(primaryExp.getExp());
            }
            else if (primaryExp.getNumber() != null) {
                return Integer.parseInt(primaryExp.getNumber().getIntConst().getContent());
            }
            else if (primaryExp.getLVal() != null) {
                return visitLValAsConst(primaryExp.getLVal());
            }
        }
        else if (unaryExp.getUnaryExp() != null) {
            int rhs = visitUnaryExp(unaryExp.getUnaryExp());
            TokenType op = unaryExp.getUnaryOp().getOp().getType();
            if (op == TokenType.MINU) return -rhs;
            else if (op == TokenType.PLUS) return rhs;
            else return (rhs == 0) ? 1 : 0;
        }
        else if (unaryExp.getIdent() != null) {
            // 在编译时求值中不允许函数调用
            return 0;
        }
        return 0;
    }

    private int visitLValAsConst(LVal lVal) {
        Symbol symbol = scopeManager.resolve(lVal.getIdent().getContent());

        if (symbol == null || !(symbol instanceof VarSymbol)) {
            // 错误：在编译时求值中使用了未定义的或非变量的标识符
            errorHandler.addError(ErrorType.UndefinedIdent,lVal.getIdent().getLine());
            return 0;
        }

        VarSymbol varSymbol = (VarSymbol) symbol;
        InitialValue initialValue = varSymbol.getInitialValue();

        if (initialValue == null) {
            // 错误：这个变量没有一个可在编译时确定的初始值
            return 0;
        }

        ArrayList<Integer> elements = initialValue.getElements();
        if (elements == null || elements.isEmpty()) {
            return 0; // 变量被默认初始化为 0
        }

        if (lVal.getIndex() == null) {
            return elements.get(0);
        } else {
            Exp indexExp = lVal.getIndex();
            // 递归调用 calculate(Exp) 来计算索引值
            int index = this.calculate(indexExp);

            if (index < 0 || index >= elements.size()) {
                // 错误：数组访问越界
                return 0;
            }
            return elements.get(index);
        }
    }

    public ArrayList<Integer> calculateInitVal(ConstInitVal initVal) {
        ArrayList<Integer> values = new ArrayList<>();
        if (initVal == null) return values;
        if (initVal.getSingleValue() != null) {
            values.add(calculate(initVal.getSingleValue()));
        } else if (initVal.getArrayValues() != null) {
            for (ConstExp exp : initVal.getArrayValues()) {
                values.add(calculate(exp));
            }
        }
        return values;
    }

    public ArrayList<Integer> calculateInitVal(InitVal initVal) {
        ArrayList<Integer> values = new ArrayList<>();
        if (initVal == null) return values;
        if (initVal.getSingleValue() != null) {
            // 直接调用 calculate(Exp)
            values.add(this.calculate(initVal.getSingleValue()));
        } else if (initVal.getArrayValues() != null) {
            for (Exp exp : initVal.getArrayValues()) {
                values.add(this.calculate(exp));
            }
        }
        return values;
    }
}
