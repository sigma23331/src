package frontend.syntax.variable;

import frontend.Token.TokenType;
import frontend.syntax.BType;
import java.util.ArrayList;

//常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';' // i

public class ConstDecl extends Decl {
    private final BType bType;
    private final ArrayList<ConstDef> constDefs;

    public ConstDecl(BType bType, ArrayList<ConstDef> constDefs) {
        this.bType = bType;
        this.constDefs = constDefs;
    }

    @Override
    public void print() {
        System.out.println(TokenType.printType(TokenType.CONSTTK));
        bType.print();
        constDefs.get(0).print();
        for (int i = 1; i < constDefs.size(); i++) {
            System.out.println(TokenType.printType(TokenType.COMMA));
            constDefs.get(i).print();
        }
        System.out.println(TokenType.printType(TokenType.SEMICN));
        System.out.println("<ConstDecl>");
    }

    public ArrayList<ConstDef> getConstDefs() {
        return constDefs;
    }

    public BType getBType() {
        return bType;
    }
}
