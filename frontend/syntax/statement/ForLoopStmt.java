package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.expression.Cond;

//'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt //

public class ForLoopStmt extends Stmt {
    private final ForStmt init;
    private final Cond cond;
    private final ForStmt update;
    private final Stmt loopBody;

    public ForLoopStmt(ForStmt init, Cond cond, ForStmt update, Stmt loopBody) {
        this.init = init;
        this.cond = cond;
        this.update = update;
        this.loopBody = loopBody;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.FORTK));
        System.out.println(TokenType.printType(TokenType.LPARENT));
        if (init != null) {
            init.print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        if (cond != null) {
            cond.print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        if (update != null) {
            update.print();
        }
        System.out.println(TokenType.printType(TokenType.RPARENT));
        loopBody.print();
        System.out.println("<Stmt>");
    }

    public Cond getCond() {
        return cond;
    }

    public Stmt getLoopBody() {
        return loopBody;
    }

    public ForStmt getInit() {
        return init;
    }

    public ForStmt getUpdate() {
        return update;
    }
}
