package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.expression.Cond;

// 'if' '(' Cond ')' Stmt [ 'else' Stmt ] // 1.有else 2.无else

public class IfStmt extends Stmt {
    private final Cond cond;
    private final Stmt ifStmt;
    private final Stmt elseStmt;

    public IfStmt(Cond cond, Stmt ifStmt, Stmt elseStmt) {
        this.cond = cond;
        this.ifStmt = ifStmt;
        this.elseStmt = elseStmt;
    }

    public IfStmt(Cond cond, Stmt ifStmt) {
        this.cond = cond;
        this.ifStmt = ifStmt;
        this.elseStmt = null;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.IFTK));
        System.out.println(TokenType.printType(TokenType.LPARENT));
        cond.print();
        System.out.println(TokenType.printType(TokenType.RPARENT));
        ifStmt.print();
        if (elseStmt != null) {
            System.out.println(TokenType.printType(TokenType.ELSETK));
            elseStmt.print();
        }
        System.out.println("<Stmt>");
    }

    public Stmt getElseStmt() {
        return elseStmt;
    }

    public Cond getCond() {
        return cond;
    }

    public Stmt getIfStmt() {
        return ifStmt;
    }
}