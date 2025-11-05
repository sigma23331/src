package frontend.syntax.function;

import frontend.syntax.BType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import frontend.Token.TokenType;

//函数形参 FuncFParam → BType Ident ['[' ']'] // 1.普通变量 2.一维数组变量

public class FuncFParam extends SyntaxNode {
    private final BType bType;
    private final Token ident;
    private final boolean isArray;

    public FuncFParam(BType bType, Token ident, boolean isArray) {
        this.bType = bType;
        this.ident = ident;
        this.isArray = isArray;
    }

    @Override
    public void print() {
        bType.print();
        System.out.println(ident);
        if (isArray) {
            System.out.println(TokenType.printType(TokenType.LBRACK));
            System.out.println(TokenType.printType(TokenType.RBRACK));
        }
        System.out.println("<FuncFParam>");
    }

    public Token getIdent() {
        return ident;
    }

    public BType getBType() {
        return bType;
    }

    public boolean isArray() {
        return isArray;
    }
}
