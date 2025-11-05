package frontend.Token;

public class Token {
    private final TokenType tokenType;
    private final String content;
    private final int line;  //记录报错信息

    public Token(TokenType tokenType,String content,int line) {
        this.tokenType = tokenType;
        this.content = content;
        this.line = line;
    }

    public TokenType getType() {
        return tokenType;
    }

    public int getLine() {
        return line;
    }

    public String getContent() {
        return content;
    }

    @Override
    public String toString() {
        if (this.tokenType == TokenType.IDENFR) {
            return "IDENFR " + content;
        }
        else if (this.tokenType == TokenType.STRCON) {
            return "STRCON " + content;
        }
        else if (this.tokenType == TokenType.INTCON) {
            return "INTCON " + content;
        }
        return tokenType.toString() + " " + content;
    }
}
