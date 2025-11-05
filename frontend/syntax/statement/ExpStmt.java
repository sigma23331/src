package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.expression.Exp;

//[Exp] ';' // 有无Exp两种情况；printf函数调用

public class ExpStmt extends Stmt {
    private final Exp exp;

    public ExpStmt(Exp exp) {
        this.exp = exp;
    }

    @Override
    public void print() {
        if (exp != null) {
            exp.print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<Stmt>");
    }

    public Exp getExp() {
        return exp;
    }
}