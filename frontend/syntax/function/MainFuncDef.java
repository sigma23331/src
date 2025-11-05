package frontend.syntax.function;

import frontend.Token.TokenType;
import frontend.syntax.BType;
import frontend.syntax.Block;
import frontend.syntax.SyntaxNode;
import middle.Scope;

//主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block // 存在main函数

public class MainFuncDef extends SyntaxNode {
    private final BType bType;
    private final Block block;

    public MainFuncDef(BType bType,Block block) {
        this.bType = bType;
        this.block = block;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.INTTK));
        System.out.println(TokenType.printType(TokenType.MAINTK));
        System.out.println(TokenType.printType(TokenType.LPARENT));
        System.out.println(TokenType.printType(TokenType.RPARENT));
        block.print();
        System.out.println("<MainFuncDef>");
    }

    public Block getBlock() {
        return block;
    }

    public BType getbType() {
        return bType;
    }

    private Scope scope; // 用于存储此函数对应的作用域
    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public Scope getScope() {
        return this.scope;
    }
}
