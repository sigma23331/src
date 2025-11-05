package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.Exp;
import frontend.syntax.expression.LVal;
import java.util.ArrayList;

// 语法规则: ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
// 这意味着 ForStmt 包含一个或多个赋值操作
public class ForStmt extends SyntaxNode {
    // 使用两个列表来分别存储所有的左值 (LVal) 和表达式 (Exp)
    // lVals.get(i) 对应 exps.get(i)
    private final ArrayList<LVal> lVals;
    private final ArrayList<Exp> exps;

    /**
     * 构造函数现在接受 LVal 的列表和 Exp 的列表
     * @param lVals 在 for 语句部分的所有左值
     * @param exps 对应的所有表达式
     */
    public ForStmt(ArrayList<LVal> lVals, ArrayList<Exp> exps) {
        // 在实际的 Parser 中，应确保两个列表的大小相等
        this.lVals = lVals;
        this.exps = exps;
    }

    @Override
    public void print() {
        lVals.get(0).print();
        System.out.println(TokenType.printType(TokenType.ASSIGN));
        exps.get(0).print();
        for (int i = 1; i < lVals.size(); i++) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            lVals.get(i).print();
            System.out.println(TokenType.printType(TokenType.ASSIGN));
            exps.get(i).print();
        }
        System.out.println("<ForStmt>");
    }

    public ArrayList<Exp> getExps() {
        return exps;
    }

    public ArrayList<LVal> getLVals() {
        return lVals;
    }
}