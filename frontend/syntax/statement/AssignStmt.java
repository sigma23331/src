package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.expression.Exp;
import frontend.syntax.expression.LVal;

// LVal '=' Exp ';'

public class AssignStmt extends Stmt {
    private final LVal lVal;
    private final Exp exp;

    public AssignStmt(LVal lVal, Exp exp) {
        this.lVal = lVal;
        this.exp = exp;
    }

    @Override
    public void print() {
        lVal.print();
        System.out.println(TokenType.printType(TokenType.ASSIGN));
        exp.print();
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<Stmt>");
    }

    public Exp getExp() {
        return exp;
    }

    public LVal getLVal() {
        return lVal;
    }

    public int getLine() {
        return lVal.getIdent().getLine();
    }
}
