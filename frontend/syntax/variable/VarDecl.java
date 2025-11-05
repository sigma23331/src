package frontend.syntax.variable;

import frontend.Token.TokenType;
import frontend.syntax.BType;
import frontend.Token.Token;
import java.util.ArrayList;

//变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';' // 1.花括号内重复0次 2.花括
//号内重复多次

public class VarDecl extends Decl {
    private final Token staticToken;
    private final BType bType;
    private final ArrayList<VarDef> varDefs;

    public VarDecl(Token staticToken, BType bType, ArrayList<VarDef> varDefs) {
        this.staticToken = staticToken;
        this.bType = bType;
        this.varDefs = varDefs;
    }

    @Override
    public void print() {
        if (staticToken != null) {
            System.out.println(TokenType.printType(TokenType.STATICTK));
        }
        bType.print();
        varDefs.get(0).print();
        for (int i = 1; i < varDefs.size(); i++) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            varDefs.get(i).print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<VarDecl>");
    }

    public BType getBType() {
        return bType;
    }

    public ArrayList<VarDef> getVarDefs() {
        return varDefs;
    }

    public Boolean isStatic() {
        return staticToken != null;
    }
}
