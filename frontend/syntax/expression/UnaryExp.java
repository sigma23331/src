package frontend.syntax.expression;

import frontend.Token.TokenType;
import frontend.syntax.SyntaxNode;
import frontend.syntax.function.FuncRParams;
import frontend.Token.Token;
import frontend.syntax.statement.ForStmt;

import java.util.ArrayList;

// UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp

public class UnaryExp extends SyntaxNode {
    private final PrimaryExp primaryExp;
    private final Token funcIdent;
    private final FuncRParams rParams;
    private final UnaryOp unaryOp;
    private final UnaryExp unaryExp;

    // Constructors for each case...
    public UnaryExp(PrimaryExp primaryExp) {
        this.primaryExp = primaryExp;
        this.funcIdent = null;
        this.rParams = null;
        this.unaryOp = null;
        this.unaryExp = null;
    }

    public UnaryExp(Token funcIdent, FuncRParams rParams) {
        this.primaryExp = null;
        this.funcIdent = funcIdent;
        this.rParams = rParams;
        this.unaryOp = null;
        this.unaryExp = null;
    }

    public UnaryExp(UnaryOp unaryOp, UnaryExp unaryExp) {
        this.primaryExp = null;
        this.funcIdent = null;
        this.rParams = null;
        this.unaryOp = unaryOp;
        this.unaryExp = unaryExp;
    }

    @Override
    public void print() {
        if (primaryExp != null) {
            primaryExp.print();
        } else if (funcIdent != null) { // Function call
            System.out.println(funcIdent);
            System.out.println(TokenType.printType(TokenType.LPARENT));
            if (rParams != null) {
                rParams.print();
            }
            System.out.println(TokenType.printType(TokenType.RPARENT));
        } else { // UnaryOp UnaryExp
            unaryOp.print();
            unaryExp.print();
        }
        System.out.println("<UnaryExp>");
    }

    public PrimaryExp getPrimaryExp() {
        return primaryExp;
    }

    public UnaryExp getUnaryExp() {
        return unaryExp;
    }

    public UnaryOp getUnaryOp() {
        return unaryOp;
    }

    public Token getIdent() {
        return funcIdent;
    }

    public Token getFuncIdent() {
        return funcIdent;
    }

    public FuncRParams getRParams() {
        return rParams;
    }

}
