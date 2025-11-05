package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import java.util.ArrayList;

/**
 * 逻辑与表达式节点
 * LAndExp → EqExp | LAndExp '&&' EqExp
 * 由一个或多个 EqExp 操作数和零个或多个 '&&' 运算符构成
 */
public class LAndExp extends SyntaxNode {
    // 操作数列表，类型为更高优先级的 EqExp
    private final ArrayList<EqExp> operands;
    // 操作符列表，存储 && 对应的 Token
    private final ArrayList<Token> operators;

    public LAndExp(ArrayList<EqExp> operands, ArrayList<Token> operators) {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public void print() {
        operands.get(0).print();
        for (int i = 1; i < operands.size(); i++) {
            System.out.println("<LAndExp>");
            System.out.println(TokenType.printType(operators.get(i-1).getType()));
            operands.get(i).print();
        }
        // 再打印本节点的类型
        System.out.println("<LAndExp>");
    }

    public ArrayList<EqExp> getEqExps() {
        return operands;
    }
}
