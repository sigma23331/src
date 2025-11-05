package frontend.Token;

public enum TokenType {
    // 标识符 & 字面量
    IDENFR("Ident"),
    INTCON("IntConst"),
    STRCON("StringConst"),

    // 关键字
    CONSTTK("const"),
    INTTK("int"),
    STATICTK("static"),
    BREAKTK("break"),
    CONTINUETK("continue"),
    IFTK("if"),
    MAINTK("main"),
    ELSETK("else"),
    FORTK("for"),
    RETURNTK("return"),
    VOIDTK("void"),
    PRINTFTK("printf"),

    // 运算符
    NOT("!"),
    AND("&&"),
    OR("||"),
    PLUS("+"),
    MINU("-"),
    MULT("*"),
    DIV("/"),
    MOD("%"),
    LSS("<"),
    LEQ("<="),
    GRE(">"),
    GEQ(">="),
    EQL("=="),
    NEQ("!="),
    ASSIGN("="),

    // 分隔符
    SEMICN(";"),
    COMMA(","),
    LPARENT("("),
    RPARENT(")"),
    LBRACK("["),
    RBRACK("]"),
    LBRACE("{"),
    RBRACE("}");

    private final String name;
    TokenType(String name) {
        this.name = name;
    }

    public static String printType(TokenType type) {
        StringBuilder res = new StringBuilder();
        res.append(type.name());
        res.append(" ");
        res.append(type);
        return res.toString();
    }

    @Override
    public String toString() {
        return name;
    }


}
