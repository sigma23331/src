package frontend.syntax.expression;

import frontend.syntax.SyntaxNode;
import frontend.Token.Token;

public class Number extends SyntaxNode {
    private final Token intConst;
    public Number(Token intConst) { this.intConst = intConst; }

    @Override
    public void print() {
        System.out.println(intConst);
        System.out.println("<Number>");
    }

    public Token getIntConst() {
        return intConst;
    }
}
