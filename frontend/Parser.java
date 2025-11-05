package frontend;

// ... 你的 import 语句 ...

import error.Error;
import error.ErrorHandler;
import error.ErrorType;
import frontend.syntax.*;
import frontend.syntax.expression.*;
import frontend.syntax.function.*;
import frontend.syntax.statement.*;
import frontend.syntax.variable.*;
import frontend.Token.Token;
import frontend.Token.TokenType;
import frontend.syntax.expression.Number;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Set;

import java.util.ArrayList;

public class Parser {
    private final ArrayList<Token> tokens;
    private int pos = 0;
    private static final Set<TokenType> EXP_START_TOKENS = EnumSet.of(
            TokenType.IDENFR, TokenType.INTCON, TokenType.LPARENT, // PrimaryExp 开头
            TokenType.PLUS, TokenType.MINU, TokenType.NOT         // UnaryOp 开头

    );

    public Parser(ArrayList<Token> tokens) {
        this.tokens = tokens;
    }

    // --- 核心辅助方法 ---

    /**
     * 查看当前位置的 Token，但不消耗它。
     * @param offset 偏移量，0 代表当前 Token，1 代表下一个，-1 代表上一个。
     * @return 对应位置的 Token。注意处理数组越界问题。
     */
    private Token peekToken(int offset) {
        // ... 你的逻辑 ...
        return tokens.get(pos + offset);
    }

    private Token curToken() {
        return peekToken(0);
    }

    private TokenType curTokenType() {
        return curToken().getType();
    }
    
    private void move() {
        pos++;
    }

    /**
     * 检查当前 Token 类型是否与期望的 type 匹配。
     * @param type 期望的 Token 类型。
     * @return 如果匹配，消耗当前 Token 并返回 true；否则不消耗，返回 false。
     */
    private boolean match(TokenType type) {
        // ... 你的逻辑：
        // 1. 检查当前 Token 类型是否等于 type。
        boolean res = curTokenType() == type;
        // 2. 如果是，pos++ 并返回 true。
        if (res) {
            pos++;
            return true;
        }
        // 3. 如果不是，返回 false。
        else {
            return false;
        }
    }
    

    /**
     * 公共入口方法
     */
    public CompileUnit parse() {
        return parseCompUnit();
    }

    /**
     * 解析编译单元 CompUnit → {Decl} {FuncDef} MainFuncDef
     */
    private CompileUnit parseCompUnit() {
        ArrayList<Decl> decls = new ArrayList<>();
        ArrayList<FuncDef> funcDefs = new ArrayList<>();

        // ... 你的逻辑:
        // 循环解析全局声明和函数定义
        while (pos < tokens.size()) { // 循环直到文件末尾
            // 查看接下来的几个 Token 来决策
            // 如果 peekToken(2).getType() != TokenType.LPARENT, 说明是变量声明
            if (pos + 2 < tokens.size() && peekToken(2).getType() != TokenType.LPARENT) {
                decls.add(parseDecl());
            }
            // 如果 peekToken(1).getType() == TokenType.MAINTK, 说明是主函数，跳出循环
            else if (pos + 1 < tokens.size() && peekToken(1).getType() == TokenType.MAINTK) {
                break;
            }
            // 否则就是普通函数定义
            else {
                funcDefs.add(parseFuncDef());
            }
        }

        MainFuncDef mainFuncDef = parseMainFuncDef();

        return new CompileUnit(decls, funcDefs, mainFuncDef);
    }

    /**
     * 解析主函数定义 MainFuncDef → 'int' 'main' '(' ')' Block
     */
    private MainFuncDef parseMainFuncDef() {
        // ... 你的逻辑: 严格按照顺序 consume 消耗即可
        BType type = parseBType();
        consume(TokenType.MAINTK);
        consume(TokenType.LPARENT);
        consume(TokenType.RPARENT);
        Block block = parseBlock();
        return new MainFuncDef(type,block);
    }

    /**
     * 解析基本表达式 PrimaryExp → '(' Exp ')' | LVal | Number
     */
    private PrimaryExp parsePrimaryExp() {
        if (match(TokenType.LPARENT)) {
            // ... 逻辑：处理括号表达式 ...
            Exp exp = parseExp();
            consume(TokenType.RPARENT);
            return new PrimaryExp(exp);
        } else if (match(TokenType.INTCON)) {
            // ... 逻辑：处理数字。注意 match 之后要用 curToken(-1) 获取刚消耗的 Token
            Number num = new Number(peekToken(-1));
            return new PrimaryExp(num);
        } else {
            // ... 逻辑：剩下的情况是 LVal
            LVal lval = parseLVal();
            return new PrimaryExp(lval);
        }
    }

    /**
     * 解析左值 LVal → Ident ['[' Exp ']']
     */
    private LVal parseLVal() {
        // ... 你的逻辑：
        // 1. 先强制消耗一个 IDENFR (标识符)。
        // 先强制消耗 IDENFR，如果失败，consume 会处理错误
        consume(TokenType.IDENFR);
        // 然后通过 peekToken(-1) 安全地获取刚刚被消耗的那个 Token
        Token ident = peekToken(-1);

        // 2. 检查后面是否紧跟着一个 '['
        if (match(TokenType.LBRACK)) {
            // 如果是，说明是数组访问
            Exp index = parseExp();
            consume(TokenType.RBRACK);
            return new LVal(ident, index);
        } else {
            // 如果不是，说明是普通变量
            return new LVal(ident, null);
        }
    }
    /**
     * 解析表达式 Exp → AddExp
     * 这是外界调用表达式解析的入口
     */
    private Exp parseExp() {
        // ... 你的逻辑：
        // 1. 调用 parseAddExp()
        // 2. 将结果包装成一个 Exp 对象并返回
        return new Exp(parseAddExp());
    }

    /**
     * 解析加减表达式 AddExp → MulExp | AddExp ('+' | '−') MulExp
     */
    private AddExp parseAddExp() {
        ArrayList<MulExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();

        // ... 你的逻辑：
        // 1. 首先，解析一个 MulExp 作为第一个操作数
        operands.add(parseMulExp());

        // 2. 进入 while 循环，处理后续的 '+' 或 '-'
        while (curTokenType() == TokenType.PLUS || curTokenType() == TokenType.MINU) {
            // a. 保存当前的操作符 Token
            operators.add(curToken());
            // b. 消耗掉操作符
            move();
            // c. 解析又一个 MulExp 作为下一个操作数
            operands.add(parseMulExp());
        }

        return new AddExp(operands, operators);
    }

    /**
     * 解析乘除模表达式 MulExp → UnaryExp | MulExp ('*' | '/' | '%') UnaryExp
     */
    private MulExp parseMulExp() {
        ArrayList<UnaryExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();

        // ... 你的逻辑：
        // 这里的逻辑和 parseAddExp 完全一样，只是处理的符号和调用的方法不同！
        // 1. 先解析一个 UnaryExp
        operands.add(parseUnaryExp());
        // 2. while 循环，检查 MULT, DIV, MOD
        while (curTokenType() == TokenType.MULT || curTokenType() == TokenType.DIV || curTokenType() == TokenType.MOD) {
            operators.add(curToken());
            move();
            operands.add(parseUnaryExp());
        }

        return new MulExp(operands, operators);
    }

    /**
     * 解析一元表达式 UnaryExp (暂时简化)
     * 完整语法: UnaryExp → PrimaryExp | Ident '(' [FuncRParams] ')' | UnaryOp UnaryExp
     * 我们先实现最基础的 PrimaryExp 部分
     */
    private UnaryExp parseUnaryExp() {
        // 1. 如果是 '+' 或 '-' 或 '!'
        if (curTokenType() == TokenType.PLUS || curTokenType() == TokenType.MINU || curTokenType() == TokenType.NOT) {
            Token opToken = curToken(); // 先获取当前的操作符
            move(); // 再消耗它
            UnaryOp op = new UnaryOp(opToken); // 用获取到的 Token 创建节点
            UnaryExp innerExp = parseUnaryExp(); // 递归解析
            return new UnaryExp(op, innerExp);
        }
        // 2. 如果是函数调用 Ident (...)
        //    这里需要超前看一位来判断
        else if (curTokenType() == TokenType.IDENFR && peekToken(1).getType() == TokenType.LPARENT) {
            // ... 你的逻辑:
            // a. 消耗 Ident 和 '('
            Token funcIdent = curToken();
            move();
            move();
            // b. 解析可选的 FuncRParams
            FuncRParams funcRParams = null;
            if (EXP_START_TOKENS.contains(curTokenType())) {
                funcRParams = parseFuncRParams();
            }
            // c. consume(')')
            consume(TokenType.RPARENT);
            // d. 返回 new UnaryExp(...) for function call
            return new UnaryExp(funcIdent,funcRParams);
        }
        // 3. 否则，就是一个 PrimaryExp
        else {
            return new UnaryExp(parsePrimaryExp());
        }
    }

    /**
     * 解析语句 Stmt
     * 这是所有语句解析的入口和调度中心
     */
    private Stmt parseStmt() {
        // 根据当前 Token 类型，分派到不同的解析逻辑
        Token lineToken = curToken();
        if (curTokenType() == TokenType.IFTK) {
            // ... 你的逻辑：解析 if 语句
            // 1. 消耗 'if'
            move();
            // 2. 消耗 '('
            consume(TokenType.LPARENT);
            // 3. 解析 Cond
            Cond cond = parseCond();
            // 4. 消耗 ')'
            consume(TokenType.RPARENT);
            // 5. 解析 Stmt (then 部分)
            Stmt thenStmt = parseStmt();
            // 6. 检查是否有 'else'，如果有，再解析一个 Stmt (else 部分)
            if (match(TokenType.ELSETK)) {
                Stmt elseStmt = parseStmt();
                return new IfStmt(cond, thenStmt, elseStmt);
            }
            else {
                return new IfStmt(cond, thenStmt);
            }
            
        } else if (curTokenType() == TokenType.LBRACE) {
            // ... 你的逻辑：解析语句块作为语句
            // 1. 调用 parseBlock()
            // 2. 将返回的 Block 包装成 BlockStmt
            return new BlockStmt(parseBlock());
            
        } else if (match(TokenType.SEMICN)) {
            return new ExpStmt(null);
        }

        // 新增 for 循环解析
        else if (match(TokenType.FORTK)) {
            // ... 你的逻辑: 解析 for 循环
            // 参考文法：'for' '(' [ForStmt] ';' [Cond] ';' [ForStmt] ')' Stmt
            // 注意处理三个部分都可能为空的情况
            ForStmt forStmt1 = null , forStmt2 = null;
            Cond cond = null;
            consume(TokenType.LPARENT);
            if (curTokenType() != TokenType.SEMICN) {
                forStmt1 = parseForStmt();
            }
            consume(TokenType.SEMICN);
            if (curTokenType() != TokenType.SEMICN) {
                cond = parseCond();
            }
            consume(TokenType.SEMICN);
            if (curTokenType() != TokenType.RPARENT) {
                forStmt2 = parseForStmt();
            }
            consume(TokenType.RPARENT);
            Stmt loopBody = parseStmt();
            return new ForLoopStmt(forStmt1,cond,forStmt2,loopBody);
        }
        // 新增 break, continue, return, printf
        else if (match(TokenType.BREAKTK)) {
            consume(TokenType.SEMICN);
            return new BreakStmt(lineToken);
        }
        else if (match(TokenType.CONTINUETK)) {
            consume(TokenType.SEMICN);
            return new ContinueStmt(lineToken);
        }
        else if (match(TokenType.RETURNTK)) {
            // ... 逻辑: 解析可选的返回值 Exp，然后 consume(';')
            Exp exp = null;
            if (EXP_START_TOKENS.contains(curTokenType())) {
                exp = parseExp();
            }
            consume(TokenType.SEMICN);
            return new ReturnStmt(lineToken,exp);
        }
        else if (match(TokenType.PRINTFTK)) {
            // ... 逻辑: consume('('), 解析 StringConst, 循环解析逗号和 Exp, consume(')'), consume(';')
            consume(TokenType.LPARENT);
            consume(TokenType.STRCON); // 强制消耗字符串
            Token formatString = peekToken(-1); // 获取刚消耗的字符串 Token
            ArrayList<Exp> exps = new ArrayList<>();
            while (match(TokenType.COMMA)) {
                exps.add(parseExp());
            }
            consume(TokenType.RPARENT);
            consume(TokenType.SEMICN);
            return new PrintfStmt(formatString,exps,lineToken);
        }

        // ... 保留你之前写的“试探与回溯”逻辑，处理赋值和表达式语句 ...
        int backtrackPos = pos;
        if (curTokenType() == TokenType.IDENFR) {
            LVal lval = parseLVal();
            if (match(TokenType.ASSIGN)) {
                Exp exp = parseExp();
                consume(TokenType.SEMICN);
                return new AssignStmt(lval, exp);
            } else {
                pos = backtrackPos;
            }
        }
        
        Exp exp = parseExp();
        consume(TokenType.SEMICN);
        return new ExpStmt(exp);
    }

    /**
     * 解析函数实参列表 FuncRParams → Exp { ',' Exp }
     */
    private FuncRParams parseFuncRParams() {
        ArrayList<Exp> params = new ArrayList<>();

        // ... 你的逻辑: 使用 do-while 循环解析一个或多个表达式
        do {
            // 1. 解析一个表达式并添加到列表中
            params.add(parseExp());
        } while (match(TokenType.COMMA)); // 只要能匹配到逗号，就继续循环

        return new FuncRParams(params);
    }

    /**
     * 解析函数定义 FuncDef → FuncType Ident '(' [FuncFParams] ')' Block
     */
    private FuncDef parseFuncDef() {
        // ... 你的逻辑: 按照顺序解析
        // 1. FuncType type = parseFuncType();
        FuncType type = parseFuncType();
        // 2. Token ident = ... (consume and peek)
        consume(TokenType.IDENFR);
        Token ident = peekToken(-1);
        // 3. consume(LPARENT);
        consume(TokenType.LPARENT);
        // 4. FuncFParams params = null; if (not ')' ) { params = parseFuncFParams(); }
        FuncFParams params = null;
        if (curTokenType() != TokenType.RPARENT && curTokenType() != TokenType.LBRACE) { //也要防止漏掉右括号的情况下解析参数
            params = parseFuncFParams();
        }
        // 5. consume(RPARENT);
        consume(TokenType.RPARENT);
        // 6. Block block = parseBlock();
        Block block = parseBlock();
        // 7. return new FuncDef(...)
        return new FuncDef(type,ident,params,block);
    }

    /**
     * 解析函数返回类型 FuncType → 'void' | 'int'
     */
    private FuncType parseFuncType() {
        // 检查当前 Token 是 void 还是 int
        if (curTokenType() == TokenType.VOIDTK || curTokenType() == TokenType.INTTK) {
            Token typeToken = curToken();
            move(); // 消耗掉 VOIDTK 或 INTTK
            return new FuncType(typeToken);
        } else {
            // 这通常是一个不应该发生的错误，因为调用 parseFuncDef 的前提就决定了这里应该是返回类型
            // 抛出一个运行时异常来表示解析器逻辑可能存在问题
            throw new RuntimeException("语法错误：期望得到函数返回类型 (void 或 int)，但实际为 " + curTokenType());
        }
    }

    /**
     * 解析函数形式参数列表 FuncFParams → FuncFParam { ',' FuncFParam }
     */
    private FuncFParams parseFuncFParams() {
        ArrayList<FuncFParam> params = new ArrayList<>();

        // 使用 do-while 循环来解析一个或多个由逗号分隔的函数参数
        do {
            params.add(parseFuncFParam());
        } while (match(TokenType.COMMA));

        return new FuncFParams(params);
    }

    /**
     * 解析单个函数形式参数 FuncFParam → BType Ident ['[' ']']
     */
    private FuncFParam parseFuncFParam() {
        // 1. 解析参数类型
        BType bType = parseBType();

        // 2. 解析参数名
        consume(TokenType.IDENFR);
        Token ident = peekToken(-1);

        // 3. 检查是否为数组形式
        boolean isArray = false;
        if (match(TokenType.LBRACK)) {
            // 函数参数的数组形式是空的方括号
            consume(TokenType.RBRACK);
            isArray = true;
        }

        return new FuncFParam(bType, ident, isArray);
    }

    /**
     * 解析 For 语句中的赋值部分
     * ForStmt → LVal '=' Exp { ',' LVal '=' Exp }
     */
    private ForStmt parseForStmt() {
        ArrayList<LVal> lvals = new ArrayList<>();
        ArrayList<Exp> exps = new ArrayList<>();

        // ... 你的逻辑: 使用 do-while 循环处理一个或多个赋值语句
        do {
            // 1. 解析 LVal
            LVal lval = parseLVal();
            // 2. 强制消耗 '='
            consume(TokenType.ASSIGN);
            // 3. 解析 Exp
            Exp exp = parseExp();

            // 4. 将它们添加到列表中
            lvals.add(lval);
            exps.add(exp);
        } while (match(TokenType.COMMA)); // 只要能匹配到逗号，就继续循环

        return new ForStmt(lvals, exps);
    }

    /**
     * 解析条件表达式 Cond → LOrExp
     */
    private Cond parseCond() {
        return new Cond(parseLOrExp());
    }

    /**
     * 解析逻辑或表达式 LOrExp → LAndExp { '||' LAndExp }
     */
    private LOrExp parseLOrExp() {
        ArrayList<LAndExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();
        // ... 你的逻辑 (和 parseAddExp/parseMulExp 模式完全一样)
        // 1. 先解析一个 LAndExp
        operands.add(parseLAndExp());
        // 2. while 循环，检查 '||' (OR)
        while (curTokenType() == TokenType.OR) {
            operators.add(curToken());
            move();
            operands.add(parseLAndExp());
        }
        return new LOrExp(operands, operators);
    }

    /**
     * 解析逻辑与表达式 LAndExp → EqExp { '&&' EqExp }
     */
    private LAndExp parseLAndExp() {
        //... 你的逻辑 (模式同上)
        ArrayList<EqExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();
        // 1. 先解析一个 EqExp
        operands.add(parseEqExp());
        // 2. while 循环，检查 '&&' (AND)
        while (curTokenType() == TokenType.AND) {
            operators.add(curToken());
            move();
            operands.add(parseEqExp());
        }
        return new LAndExp(operands, operators);
    }

    /**
     * 解析相等性表达式 EqExp → RelExp { ('==' | '!=') RelExp }
     */
    private EqExp parseEqExp() {
        // ... 你的逻辑 (模式同上)
        ArrayList<RelExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();
        // 1. 先解析一个 RelExp
        operands.add(parseRelExp());
        // 2. while 循环，检查 '==' (EQ) 或 '!=' (NE)  
        while (curTokenType() == TokenType.EQL || curTokenType() == TokenType.NEQ) {
            operators.add(curToken());
            move();
            operands.add(parseRelExp());    
        }
        return new EqExp(operands, operators);
    }

    /**
     * 解析关系表达式 RelExp → AddExp { ('<' | '>' | '<=' | '>=') AddExp }
     */
    private RelExp parseRelExp() {
        // ... 你的逻辑 (模式同上)
        ArrayList<AddExp> operands = new ArrayList<>();
        ArrayList<Token> operators = new ArrayList<>();
        // 1. 先解析一个 AddExp
        operands.add(parseAddExp());
        // 2. while 循环，检查 '<' (LT), '>' (GT), '<=' (LE), '>=' (GE)
        while (curTokenType() == TokenType.LSS || curTokenType() == TokenType.GRE || curTokenType() == TokenType.LEQ || curTokenType() == TokenType.GEQ) {
            operators.add(curToken());
            move();
            operands.add(parseAddExp());
        }
        return new RelExp(operands, operators);
    }

    /**
     * 解析语句块 Block → '{' { BlockItem } '}'
     */
    private Block parseBlock() {
        ArrayList<BlockItem> blockItems = new ArrayList<>();
        // 1. 强制消耗左花括号 '{'
        consume(TokenType.LBRACE);

        // 2. 循环解析 BlockItem，直到遇到右花括号 '}'
        while (curTokenType() != TokenType.RBRACE) {
            blockItems.add(parseBlockItem());
        }

        // 3. 强制消耗右花括号 '}'
        Token finalToken = curToken();
        consume(TokenType.RBRACE);

        return new Block(blockItems,finalToken);
    }

    /**
     * 解析语句块项 BlockItem → Decl | Stmt
     */
    private BlockItem parseBlockItem() {
        // ... 你的逻辑:
        // 通过查看当前 Token 来判断是声明还是语句
        if (curTokenType() == TokenType.CONSTTK || curTokenType() == TokenType.INTTK || curTokenType() == TokenType.STATICTK) {
             // 开头是 const 或 int，说明是声明
             return parseDecl(); // 我们将在下一步实现 parseDecl
         } else {
             // 否则，就是一条语句
             return parseStmt();
         }
    }

    /**
     * 解析声明 Decl → ConstDecl | VarDecl
     */
    private Decl parseDecl() {
        // ... 你的逻辑：
        // 1. 查看当前 Token 是否为 'const'
        if (curTokenType() == TokenType.CONSTTK) {
             return parseConstDecl();
        } else {
             return parseVarDecl();
        }
    }

    /**
     * 解析变量声明 VarDecl → [ 'static' ] BType VarDef { ',' VarDef } ';'
     */
    private VarDecl parseVarDecl() {
        // 1. 处理可选的 'static'
        Token staticToken = null;
        if (match(TokenType.STATICTK)) {
             staticToken = peekToken(-1); // 获取刚消耗的 'static'
        }

        // 2. 解析基本类型 BType
        if (curTokenType() != TokenType.INTTK) {
            ErrorHandler.getInstance().addError(ErrorType.UndefinedIdent,curToken().getLine());
        }
        BType bType = parseBType();

        // 3. 解析一个或多个 VarDef
        ArrayList<VarDef> varDefs = new ArrayList<>();
        do {
            varDefs.add(parseVarDef());
        } while (match(TokenType.COMMA)); // 只要能匹配到逗号就继续

        // 4. 强制消耗分号
        consume(TokenType.SEMICN);

        return new VarDecl(staticToken, bType, varDefs);
    }

    /**
     * 常量声明 ConstDecl → 'const' BType ConstDef { ',' ConstDef } ';'
     */
    private ConstDecl parseConstDecl() {
        consume(TokenType.CONSTTK);

        BType bType = parseBType();
        ArrayList<ConstDef> constDefs = new ArrayList<>();
        do {
            constDefs.add(parseConstDef());
        } while (match(TokenType.COMMA));
        consume(TokenType.SEMICN);

        return new ConstDecl(bType,constDefs);
    }

    /**
     * 解析基本类型 BType → 'int'
     */
    private BType parseBType() {
        // ... 你的逻辑:
        // 在我们的文法中，BType 只可能是 'int'
        consume(TokenType.INTTK);
        return new BType(peekToken(-1));
    }

    /**
     * 解析变量定义 VarDef → Ident [ '[' ConstExp ']' ] [ '=' InitVal ]
     */
    private VarDef parseVarDef() {
        // ... 你的逻辑:
        // 1. 解析标识符 Ident
        consume(TokenType.IDENFR);
        Token ident = peekToken(-1);

        // 2. 解析可选的数组维度 [ ConstExp ]
        ConstExp arraySize = null;
        if (match(TokenType.LBRACK)) {
           arraySize = parseConstExp();
           consume(TokenType.RBRACK);
        }

        // 3. 解析可选的初始化部分 = InitVal
        InitVal initVal = null;
        if (match(TokenType.ASSIGN)) {
            initVal = parseInitVal();
        }

        return new VarDef(ident, arraySize, initVal);
    }

    /**
     * 常量定义 ConstDef → Ident [ '[' ConstExp ']' ] '=' ConstInitVal
     */
    private ConstDef parseConstDef() {
        consume(TokenType.IDENFR);
        Token ident = peekToken(-1);
        ConstExp arraySize = null;
        if (match(TokenType.LBRACK)) {
            arraySize = parseConstExp();
            consume(TokenType.RBRACK);
        }
        consume(TokenType.ASSIGN); // 必须要有 '='
        ConstInitVal constInitVal = parseConstInitVal(); // 并且必须要有初始化值
        return new ConstDef(ident,arraySize,constInitVal);
    }

    /**
     * 解析变量初值 InitVal → Exp | '{' [ Exp { ',' Exp } ] '}'
     */
    private InitVal parseInitVal() {
        if (curTokenType() == TokenType.LBRACE) {
            // 是，解析数组初始化
            consume(TokenType.LBRACE);
            ArrayList<Exp> values = new ArrayList<>();
            if (curTokenType() != TokenType.RBRACE) {
                do {
                    values.add(parseExp());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RBRACE);
            return new InitVal(values);
        }
        // 新增的分支：检查是否为字符串常量
        else if (match(TokenType.STRCON)) {
            return new InitVal(peekToken(-1)); // 使用刚消耗的 STRCON Token 创建节点
        }
        else {
            // 否，解析单个表达式初始化
            return new InitVal(parseExp());
        }
    }

    /**
     * 常量初值 ConstInitVal → ConstExp | '{' [ ConstExp { ',' ConstExp } ] '}'
     */
    private ConstInitVal parseConstInitVal() {
        if (curTokenType() == TokenType.LBRACE) {
            // 是，解析数组初始化
            consume(TokenType.LBRACE);
            ArrayList<ConstExp> values = new ArrayList<>();
            if (curTokenType() != TokenType.RBRACE) {
                do {
                    values.add(parseConstExp());
                } while (match(TokenType.COMMA));
            }
            consume(TokenType.RBRACE);
            return new ConstInitVal(values);
        }
        // 新增的分支：检查是否为字符串常量
        else if (match(TokenType.STRCON)) {
            return new ConstInitVal(peekToken(-1)); // 使用刚消耗的 STRCON Token 创建节点
        }
        else {
            // 否，解析单个常量表达式初始化
            return new ConstInitVal(parseConstExp());
        }
    }

    // ConstExp 是常量表达式，文法定义为 AddExp，我们暂时直接调用 parseAddExp
    private ConstExp parseConstExp() {
        return new ConstExp(parseAddExp());
    }

    private void consume(TokenType type) {
        if (match(type)) {
            // 类型匹配，match 方法已经消耗了 Token，一切正常
            return;
        }

        // --- 类型不匹配，进入错误处理逻辑 ---

        // 1. 根据期望的 Token 类型，确定错误类型
        ErrorType errorType;
        switch (type) {
            case SEMICN:
                errorType = ErrorType.MissingSemicolon;
                break;
            case RPARENT:
                errorType = ErrorType.MissingRParent;
                break;
            case RBRACK:
                errorType = ErrorType.MissingRBrack;
                break;
            default:
                // 对于其他不可恢复的错误，可以选择抛出异常
                // 但根据题目要求，我们只处理这三种
                return;
        }

        // 2. 获取上一个 Token 的行号进行报错
        // 因为我们没有消耗当前（错误的）Token，所以 pos 没变，peekToken(-1) 依然是正确的上一个 Token
        int line = peekToken(-1).getLine();

        // 3. 使用 ErrorHandler 记录错误
        ErrorHandler.getInstance().addError(errorType, line);
    }
}
