package frontend.syntax.function;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import middle.Scope;

//函数类型 FuncType → 'void' | 'int' // 覆盖两种类型的函数

public class FuncType extends SyntaxNode {
    private final Token typeToken;

    public FuncType(Token typeToken) {
        this.typeToken = typeToken;
    }

    @Override
    public void print() {
        // 叶子节点
        System.out.println(TokenType.printType(typeToken.getType()));
        System.out.println("<FuncType>");
    }

    public TokenType getType() {
        return typeToken.getType();
    }

    private Scope scope; // 用于存储此函数对应的作用域

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return this.scope;
    }
}