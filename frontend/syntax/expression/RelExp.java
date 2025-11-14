package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.Token.Token;
import java.util.ArrayList;

/**
 * 关系表达式节点
 * RelExp → AddExp | RelExp ('<' | '>' | '<=' | '>=') AddExp
 * 由一个或多个 AddExp 操作数和零个或多个关系运算符构成
 */
public class RelExp extends SyntaxNode {
    // 操作数列表，类型为更高优先级的 AddExp
    private final ArrayList<AddExp> operands;
    // 操作符列表，存储 < > <= >= 对应的 Token
    private final ArrayList<Token> operators;

    public RelExp(ArrayList<AddExp> operands, ArrayList<Token> operators) {
        this.operands = operands;
        this.operators = operators;
    }

    @Override
    public void print() {
        operands.get(0).print();
        for (int i = 1; i < operands.size(); i++) {
            System.out.println("<RelExp>");
            System.out.println(TokenType.printType(operators.get(i-1).getType()));
            operands.get(i).print();
        }
        // 再打印本节点的类型
        System.out.println("<RelExp>");
    }

    public ArrayList<AddExp> getAddExps() {
        return operands;
    }

    public ArrayList<Token> getOperators() {
        return operators;
    }
}