package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;

//左值表达式 LVal → Ident ['[' Exp ']'] // 1.普通变量、常量 2.一维数组

public class LVal extends SyntaxNode {
    private final Token ident;
    private final Exp index;

    public LVal(Token ident, Exp index) {
        this.ident = ident;
        this.index = index;
    }

    @Override
    public void print() {
        System.out.println(ident);
        if (index != null) {
            System.out.println(TokenType.printType(TokenType.LBRACK));
            index.print();
            System.out.println(TokenType.printType(TokenType.RBRACK));
        }
        System.out.println("<LVal>");
    }

    public Token getIdent() {
        return ident;
    }

    public Exp getIndex() {
        return index;
    }
}
