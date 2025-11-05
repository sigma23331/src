package frontend.syntax.variable;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.ConstExp;
import frontend.Token.Token;

//变量定义 VarDef → Ident [ '[' ConstExp ']' ] | Ident [ '[' ConstExp ']' ] '=' InitVal

public class VarDef extends SyntaxNode {
    private final Token ident;
    private final ConstExp arraySize;
    private final InitVal initVal;

    public VarDef(Token ident, ConstExp arraySize, InitVal initVal) {
        this.ident = ident;
        this.arraySize = arraySize;
        this.initVal = initVal;
    }

    @Override
    public void print() {
        System.out.println(ident);
        if (arraySize != null) {
            System.out.println(TokenType.printType(TokenType.LBRACK));
            arraySize.print();
            System.out.println(TokenType.printType(TokenType.RBRACK));
        }
        if (initVal != null) {
            System.out.println(TokenType.printType(TokenType.ASSIGN));
            initVal.print();
        }
        System.out.println("<VarDef>");
    }

    public Token getIdent() {
        return ident;
    }

    public ConstExp getArraySize() {
        return arraySize;
    }

    public InitVal getInitVal() {
        return initVal;
    }
}