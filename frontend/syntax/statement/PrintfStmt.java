package frontend.syntax.statement;

import frontend.Token.TokenType;
import frontend.syntax.expression.Exp;
import frontend.Token.Token;
import java.util.ArrayList;

//'printf''('StringConst {','Exp}')'';' // 1.有Exp 2.无Exp

public class PrintfStmt extends Stmt {
    private final Token lineToken;
    private final Token formatString;
    private final ArrayList<Exp> exps;

    public PrintfStmt(Token formatString, ArrayList<Exp> exps,Token token) {
        this.formatString = formatString;
        this.exps = exps;
        this.lineToken = token;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.PRINTFTK));
        System.out.println(TokenType.printType(TokenType.LPARENT));
        System.out.println(formatString);
        for (Exp exp : exps) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            exp.print();
        }
        System.out.println(TokenType.printType(TokenType.RPARENT));
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<Stmt>");
    }

    public Token getFormatString() {
        return formatString;
    }

    public ArrayList<Exp> getExps() {
        return exps;
    }

    public int getLine() {
        return lineToken.getLine();
    }
}