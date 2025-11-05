package frontend;

import frontend.Token.Token;
import frontend.Token.TokenType;

import error.ErrorHandler;
import error.ErrorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Lexer {
    private final String InputString;
    private int curPos = 0;
    private StringBuilder curToken = new StringBuilder();
    private TokenType curType = null;
    private int line = 1;
    private static final Map<String,TokenType> reservedMap = new HashMap<>(); //关键词（字符串）
    private static final Map<String,TokenType> reservedSingleCharMap = new HashMap<>();

    static {
        // 关键字
        reservedMap.put("const", TokenType.CONSTTK);
        reservedMap.put("int", TokenType.INTTK);
        reservedMap.put("static", TokenType.STATICTK);
        reservedMap.put("break", TokenType.BREAKTK);
        reservedMap.put("continue", TokenType.CONTINUETK);
        reservedMap.put("if", TokenType.IFTK);
        reservedMap.put("main", TokenType.MAINTK);
        reservedMap.put("else", TokenType.ELSETK);
        reservedMap.put("for", TokenType.FORTK);
        reservedMap.put("return", TokenType.RETURNTK);
        reservedMap.put("void", TokenType.VOIDTK);
        reservedMap.put("printf", TokenType.PRINTFTK);

        // 单字符运算符
        reservedSingleCharMap.put("+", TokenType.PLUS);
        reservedSingleCharMap.put("-", TokenType.MINU);
        reservedSingleCharMap.put("*", TokenType.MULT);
        reservedSingleCharMap.put("%", TokenType.MOD);

        // 单字符分隔符
        reservedSingleCharMap.put(";", TokenType.SEMICN);
        reservedSingleCharMap.put(",", TokenType.COMMA);
        reservedSingleCharMap.put("(", TokenType.LPARENT);
        reservedSingleCharMap.put(")", TokenType.RPARENT);
        reservedSingleCharMap.put("[", TokenType.LBRACK);
        reservedSingleCharMap.put("]", TokenType.RBRACK);
        reservedSingleCharMap.put("{", TokenType.LBRACE);
        reservedSingleCharMap.put("}", TokenType.RBRACE);
    }

    public Lexer(String inputString) {
        this.InputString = inputString;
    }

    public ArrayList<Token> tokenize() {
        ArrayList<Token> tokens = new ArrayList<>();
        while(hasNext()) {
            tokens.add(new Token(curType,curToken.toString(),line));
        }
        return tokens;
    }

    private boolean hasNext() {
        skip(); //跳过空白字符
        if(reachEnd()) {
            return false;
        }

        UpdateCurToken();

        if(Character.isDigit(nowChar())) { //处理整数
            curType = TokenType.INTCON;
            if (nowChar() == '0') { //以0开头-》只有这一个0
                curToken.append(nowChar());
                curPos++;
            }
            else {
                while(!reachEnd() && Character.isDigit(nowChar())) {
                    curToken.append(nowChar());
                    curPos++;
                }
            }

        }

        else if (isIdentfHead(nowChar())) { //IDFR
            while(!reachEnd() &&
                    (isIdentfHead(nowChar()) || Character.isDigit(nowChar()))) {
                curToken.append(nowChar());
                curPos++;
            }

            curType = reservedMap.getOrDefault(curToken.toString(),TokenType.IDENFR);
        }

        else if (nowChar() == '/') {
            // 这部分逻辑是安全的，因为它使用了向前看 (lookahead)
            if (curPos + 1 < InputString.length()) {
                char nextChar = InputString.charAt(curPos + 1);

                if (nextChar == '/') { // 单行注释
                    curPos += 2;
                    while (!reachEnd() && nowChar() != '\n') {
                        curPos++;
                    }
                    return hasNext();
                }
                else if (nextChar == '*') { // 多行注释
                    curPos += 2; // 跳过 '/*'
                    boolean commentClosed = false;

                    while (curPos + 1 < InputString.length()) {
                        if (InputString.charAt(curPos) == '*' && InputString.charAt(curPos + 1) == '/') {
                            curPos += 2; // 找到了，跳过 '*/'
                            commentClosed = true;
                            break;
                        }

                        if (nowChar() == '\n') {
                            line++;
                        }
                        curPos++;
                    }

                    if (!commentClosed) {
                        // 可以在这里报告一个未闭合的注释错误
                        // throw new RuntimeException("Unterminated block comment at line " + line);
                    }
                    return hasNext();
                }
            }

            // 如果不是注释，则为除号
            curType = TokenType.DIV;
            curToken.append('/');
            curPos++;
        }

        else if (nowChar() == '\"') {
            curType = TokenType.STRCON;
            curToken.append(nowChar());
            curPos++;
            while (!reachEnd() && nowChar() != '\"') {
                curToken.append(nowChar());
                curPos++;
            }
            // 【修正】在添加结尾的引号前，检查是否已到文件末尾
            if (!reachEnd()) {
                curToken.append(nowChar());
                curPos++;
            }
            // 如果到了末尾，说明字符串未闭合，解析器会在之后报告语法错误
        }

        else if(reservedSingleCharMap.containsKey(String.valueOf(nowChar()))) { //单个保留字符
            curToken.append(nowChar());
            curType = reservedSingleCharMap.get(String.valueOf(nowChar()));
            curPos++;
        }

        else if(nowChar() == '>') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '=') {
                curToken.append(nowChar());
                curType = TokenType.GEQ;
                curPos++;
            }
            else {
                curType = TokenType.GRE;
            }
        }

        else if(nowChar() == '<') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '=') {
                curToken.append(nowChar());
                curType = TokenType.LEQ;
                curPos++;
            }
            else {
                curType = TokenType.LSS;
            }
        }

        else if(nowChar() == '=') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '=') {
                curToken.append(nowChar());
                curType = TokenType.EQL;
                curPos++;
            }
            else {
                curType = TokenType.ASSIGN;
            }
        }

        else if(nowChar() == '!') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '=') {
                curToken.append(nowChar());
                curType = TokenType.NEQ;
                curPos++;
            }
            else {
                curType = TokenType.NOT;
            }
        }

        else if(nowChar() == '|') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '|') {
                curToken.append(nowChar());
                curType = TokenType.OR;
                curPos++;
            }
            else {
                curType = TokenType.OR; // 即使只有一个 '|'，也识别为 OR，但报告错误
                ErrorHandler.getInstance().addError(ErrorType.IllegalSymbol,line);
            }
        }

        else if(nowChar() == '&') {
            curToken.append(nowChar());
            curPos++;
            // 【修正】在访问下一个字符前，增加边界检查
            if (!reachEnd() && nowChar() == '&') {
                curToken.append(nowChar());
                curType = TokenType.AND;
                curPos++;
            }
            else {
                curType = TokenType.AND; // 即使只有一个 '&'，也识别为 AND，但报告错误
                ErrorHandler.getInstance().addError(ErrorType.IllegalSymbol,line);
            }
        }

        else {
            // 处理非法字符，报告错误并继续
            ErrorHandler.getInstance().addError(ErrorType.IllegalSymbol, line);
            curPos++;
            return hasNext();
        }

        return curType != null;
    }

    private char nowChar() {
        return InputString.charAt(curPos);
    }

    private boolean isBlank(char c) {
        return c == '\n' || c == '\t' || c == '\r' || c == ' ';
    }

    private void UpdateLine(char c) {
        if(c == '\n') {
            line++;
        }
    }

    private void skip() {
        while(!reachEnd() && isBlank(nowChar())) {
            UpdateLine(nowChar());
            curPos++;
        }
    }

    private void UpdateCurToken() {
        curToken = new StringBuilder();
        curType = null;
    }

    private boolean reachEnd() {
        return curPos >= InputString.length();
    }

    public boolean isIdentfHead(char c) {
        return Character.isLetter(c) || c == '_';
    }
}
