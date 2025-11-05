package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import java.util.ArrayList;

/**
 * 相等性表达式节点
 * EqExp → RelExp | EqExp ('==' | '!=') RelExp
 * 由一个或多个 RelExp 操作数和零个或多个相等性运算符构成
 */
public class EqExp extends SyntaxNode {
    // 操作数列表，类型为更高优先级的 RelExp
    private final ArrayList<RelExp> operands;
    // 操作符列表，存储 == != 对应的 Token
    private final ArrayList<Token> operators;

    public EqExp(ArrayList<RelExp> operands, ArrayList<Token> operators) {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public void print() {
        operands.get(0).print();
        for (int i = 1; i < operands.size(); i++) {
            System.out.println("<EqExp>");
            System.out.println(TokenType.printType(operators.get(i-1).getType()));
            operands.get(i).print();
        }
        System.out.println("<EqExp>");
    }

    public ArrayList<RelExp> getRelExps() {
        return operands;
    }
}