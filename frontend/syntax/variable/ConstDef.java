package frontend.syntax.variable;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.ConstExp;
import frontend.Token.Token;

//常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal // k

public class ConstDef extends SyntaxNode {
    private final Token ident;
    private final ConstExp arraySize;
    private final ConstInitVal constInitVal;

    public ConstDef(Token ident, ConstExp arraySize, ConstInitVal constInitVal) {
        this.ident = ident;
        this.arraySize = arraySize;
        this.constInitVal = constInitVal;
    }

    @Override
    public void print() {
        System.out.println(ident);
        if (arraySize != null) {
            System.out.println(TokenType.printType(TokenType.LBRACK));
            arraySize.print();
            System.out.println(TokenType.printType(TokenType.RBRACK));
        }
        System.out.println(TokenType.printType(TokenType.ASSIGN));
        constInitVal.print();
        System.out.println("<ConstDef>");
    }

    public ConstExp getArraySize() {
        return arraySize;
    }

    public Token getIdent() {
        return ident;
    }

    public ConstInitVal getConstInitVal() {
        return constInitVal;
    }
}
