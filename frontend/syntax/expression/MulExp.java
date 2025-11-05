package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import java.util.ArrayList;

//乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp

public class MulExp extends SyntaxNode {
    private final ArrayList<UnaryExp> operands;
    private final ArrayList<Token> operators;

    public MulExp(ArrayList<UnaryExp> operands, ArrayList<Token> operators) {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public void print() {
        operands.get(0).print();
        for (int i = 1; i < operands.size(); i++) {
            System.out.println("<MulExp>");
            System.out.println(TokenType.printType(operators.get(i-1).getType()));
            operands.get(i).print();
        }
        System.out.println("<MulExp>");
    }

    public ArrayList<Token> getOperators() {
        return operators;
    }

    public ArrayList<UnaryExp> getUnaryExps() {
        return operands;
    }
}
