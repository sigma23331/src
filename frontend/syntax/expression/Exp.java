package frontend.syntax.expression;

import frontend.syntax.SyntaxNode;

//Exp → AddExp

public class Exp extends SyntaxNode {
    private final AddExp addExp;
    public Exp(AddExp addExp) { this.addExp = addExp; }

    @Override
    public void print() {
        addExp.print();
        System.out.println("<Exp>");
    }

    public ConstExp toConstExp() {
        return new ConstExp(addExp);
    }

    public AddExp getAddExp() {
        return addExp;
    }
}
