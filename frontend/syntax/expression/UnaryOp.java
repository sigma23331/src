package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;

//单目运算符 UnaryOp → '+' | '−' | '!' 注：'!'仅出现在条件表达式中 // 三种均需覆盖

public class UnaryOp extends SyntaxNode {
    private final Token opToken;
    public UnaryOp(Token opToken) { this.opToken = opToken; }

    @Override
    public void print() {
        System.out.println(TokenType.printType(opToken.getType()));
        System.out.println("<UnaryOp>");
    }

    public Token getOp() {
        return opToken;
    }
}
