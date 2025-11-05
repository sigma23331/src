package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import java.util.ArrayList;

//加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp // 1.MulExp 2.+ 需覆盖 3.- 需覆盖

public class AddExp extends SyntaxNode {
    private final ArrayList<MulExp> operands;
    private final ArrayList<Token> operators;

    public AddExp(ArrayList<MulExp> operands, ArrayList<Token> operators) {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public void print() {
        operands.get(0).print();
        for (int i = 1; i < operands.size(); i++) {
            System.out.println("<AddExp>");
            System.out.println(TokenType.printType(operators.get(i-1).getType()));
            operands.get(i).print();
        }
        System.out.println("<AddExp>");
    }

    public ArrayList<MulExp> getMulExps() {
        return operands;
    }

    public ArrayList<Token> getOperators() {
        return operators;
    }
}
