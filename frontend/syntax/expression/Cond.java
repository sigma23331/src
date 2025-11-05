package frontend.syntax.expression;

import frontend.syntax.SyntaxNode;

//条件表达式 Cond → LOrExp

public class Cond extends SyntaxNode {
    private final LOrExp lOrExp;
    public Cond(LOrExp lOrExp) { this.lOrExp = lOrExp; }

    @Override
    public void print() {
        lOrExp.print();
        System.out.println("<Cond>");
    }

    public LOrExp getLOrExp() {
        return lOrExp;
    }
}
