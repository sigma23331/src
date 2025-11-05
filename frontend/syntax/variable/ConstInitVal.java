package frontend.syntax.variable;

import frontend.Token.Token;
import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.ConstExp;
import java.util.ArrayList;

//常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}' // 1.常表达式初值 2.一维数组初值

public class ConstInitVal extends SyntaxNode {
    private final ConstExp singleValue;
    private final ArrayList<ConstExp> arrayValues;
    private final Token stringConst;

    public ConstInitVal(ConstExp singleValue) {
        this.singleValue = singleValue;
        this.arrayValues = null;
        this.stringConst = null;
    }
    public ConstInitVal(ArrayList<ConstExp> arrayValues) {
        this.singleValue = null;
        this.arrayValues = arrayValues;
        this.stringConst = null;
    }

    public ConstInitVal(Token stringConst) {
        this.singleValue = null;
        this.arrayValues = null;
        this.stringConst = stringConst;
    }

    @Override
    public void print() {
        if (singleValue != null) {
            singleValue.print();
        } else if (arrayValues != null) {
            System.out.println(TokenType.printType(TokenType.LBRACE));
            arrayValues.get(0).print();
            for (int i = 1; i < arrayValues.size(); i++) {
                System.out.println(TokenType.printType(TokenType.COMMA));
                arrayValues.get(i).print();
            }
            System.out.println(TokenType.printType(TokenType.RBRACE));
        } else if (stringConst != null) {
            System.out.println(stringConst);
        } else {
            System.out.println(TokenType.printType(TokenType.LBRACE));
            System.out.println(TokenType.printType(TokenType.RBRACE));
        }
        System.out.println("<ConstInitVal>");
    }

    public Token getStringConst() {
        return stringConst;
    }

    public ConstExp getSingleValue() {
        return singleValue;
    }

    public ArrayList<ConstExp> getArrayValues() {
        return arrayValues;
    }
}
