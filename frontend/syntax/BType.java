package frontend.syntax;

import frontend.Token.Token;
import frontend.Token.TokenType;

// 基本类型 BType → 'int'
public class BType extends SyntaxNode {
    private final Token token;

    public BType(Token token) {
        this.token = token;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.INTTK));
        //System.out.println("<BType>"); //不用输出
    }

    public int getLine() {
        return token.getLine();
    }

    public Token getToken() {
        return token;
    }
}
