package frontend.syntax.statement;

import frontend.Token.Token;
import frontend.Token.TokenType;

public class ContinueStmt extends Stmt {
    private final Token lineToken;
    public ContinueStmt(Token token) {
        this.lineToken = token;
    }
    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.CONTINUETK));
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<Stmt>");
    }

    public int getLine() {
        return lineToken.getLine();
    }
}
