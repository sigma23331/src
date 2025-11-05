package frontend.syntax.function;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.expression.Exp;
import java.util.ArrayList;

//函数实参表达式 FuncRParams → Exp { ',' Exp } // 1.花括号内重复0次 2.花括号内重复多次 3.Exp需要覆
//盖数组传参和部分数组传参

public class FuncRParams extends SyntaxNode {
    private final ArrayList<Exp> params;

    public FuncRParams(ArrayList<Exp> params) {
        this.params = params;
    }

    @Override
    public void print() {
        params.get(0).print();
        for (int i = 1; i < params.size(); i++) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            params.get(i).print();
        }
        System.out.println("<FuncRParams>");
    }

    public ArrayList<Exp> getParams() {
        return params;
    }
}
