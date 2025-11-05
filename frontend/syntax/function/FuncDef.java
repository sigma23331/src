package frontend.syntax.function;

import frontend.syntax.Block;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import frontend.Token.TokenType;
import middle.Scope;

//函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block // 1.无形参 2.有形参

public class FuncDef extends SyntaxNode {
    private final FuncType funcType;
    private final Token ident;
    private final FuncFParams funcFParams;
    private final Block block;

    public FuncDef(FuncType funcType, Token ident, FuncFParams funcFParams, Block block) {
        this.funcType = funcType;
        this.ident = ident;
        this.funcFParams = funcFParams;
        this.block = block;
    }

    @Override
    public void print() {
        funcType.print();
        System.out.println(ident);
        System.out.println(TokenType.printType(TokenType.LPARENT));
        if (funcFParams != null) {
            funcFParams.print();
        }
        System.out.println(TokenType.printType(TokenType.RPARENT));
        block.print();
        System.out.println("<FuncDef>");
    }

    public Token getIdent() {
        return ident;
    }

    public Block getBlock() {
        return block;
    }

    public FuncType getFuncType() {
        return funcType;
    }

    public FuncFParams getFuncFParams() {
        return funcFParams;
    }

    private Scope scope; // 用于存储此函数对应的作用域

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return this.scope;
    }
}
