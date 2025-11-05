package frontend.syntax.variable;

import frontend.Token.Token;
import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.Exp;
import java.util.ArrayList;

//变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}' // 1.表达式初值 2.一维数组初值

public class InitVal extends SyntaxNode {
    private final Exp singleValue;
    private final ArrayList<Exp> arrayValues;
    private final Token stringConst;

    public InitVal(Exp singleValue) {
        this.singleValue = singleValue;
        this.arrayValues = null;
        this.stringConst = null;
    }
    public InitVal(ArrayList<Exp> arrayValues) {
        this.singleValue = null;
        this.arrayValues = arrayValues;
        this.stringConst = null;
    }

    public InitVal(Token stringConst) {
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
        System.out.println("<InitVal>");
    }

    public Token getStringConst() {
        return stringConst;
    }

    public Exp getSingleValue() {
        return singleValue;
    }

    public ArrayList<Exp> getArrayValues() {
        return arrayValues;
    }
}
