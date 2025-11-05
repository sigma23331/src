package frontend.syntax.statement;

import frontend.Token.Token;
import frontend.Token.TokenType;
import frontend.syntax.expression.Exp;

//'return' [Exp] ';' // 1.有Exp 2.无Exp

public class ReturnStmt extends Stmt {
    private final Token token;
    private final Exp exp;

    public ReturnStmt(Token token,Exp exp) {
        this.token = token;
        this.exp = exp;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.RETURNTK));
        if (exp != null) {
            exp.print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<Stmt>");
    }

    public int getLine() {
        return token.getLine();
    }

    public Exp getExp() {
        return exp;
    }
}