package frontend.syntax.function;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import java.util.ArrayList;

//函数形参表 FuncFParams → FuncFParam { ',' FuncFParam } // 1.花括号内重复0次 2.花括号内重复多次

public class FuncFParams extends SyntaxNode {
    private final ArrayList<FuncFParam> params;

    public FuncFParams(ArrayList<FuncFParam> params) {
        this.params = params;
    }

    @Override
    public void print() {
        params.get(0).print();
        for (int i = 1; i < params.size(); i++) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            params.get(i).print();
        }
        System.out.println("<FuncFParams>");
    }

    public ArrayList<FuncFParam> getParams() {
        return params;
    }
}
