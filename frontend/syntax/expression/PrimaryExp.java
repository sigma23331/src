package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;

//基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number

public class PrimaryExp extends SyntaxNode {
    private final Exp exp;
    private final LVal lVal;
    private final Number number;

    public PrimaryExp(Exp exp) {
        this.exp = exp;
        this.lVal = null;
        this.number = null;
    }
    public PrimaryExp(LVal lVal) {
        this.exp = null;
        this.lVal = lVal;
        this.number = null;
    }
    public PrimaryExp(Number number) {
        this.exp = null;
        this.lVal = null;
        this.number = number;
    }

    @Override
    public void print() {
        if (exp != null) {
            System.out.println(TokenType.printType(TokenType.LPARENT));
            exp.print();
            System.out.println(TokenType.printType(TokenType.RPARENT));
        } else if (lVal != null) {
            lVal.print();
        } else if (number != null) {
            number.print();
        }
        System.out.println("<PrimaryExp>");
    }

    public LVal getLVal() {
        return lVal;
    }

    public Exp getExp() {
        return exp;
    }

    public Number getNumber() {
        return number;
    }
}
