package miniplc0java.analyser;

import miniplc0java.error.AnalyzeError;
import miniplc0java.error.CompileError;
import miniplc0java.error.ErrorCode;
import miniplc0java.error.ExpectedTokenError;
import miniplc0java.error.TokenizeError;
import miniplc0java.instruction.Instruction;
import miniplc0java.instruction.Operation;
import miniplc0java.tokenizer.Token;
import miniplc0java.tokenizer.TokenType;
import miniplc0java.tokenizer.Tokenizer;
import miniplc0java.util.Pos;

import java.util.*;

public final class Analyser {

    Tokenizer tokenizer;
    ArrayList<Instruction> instructions;

    /** 当前偷看的 token */
    Token peekedToken = null;

    /** 符号表 */
    HashMap<String, SymbolEntry> symbolTable = new HashMap<>();

    /** 下一个变量的栈偏移 */
    int nextOffset = 0;

    public Analyser(Tokenizer tokenizer) {
        this.tokenizer = tokenizer;
        this.instructions = new ArrayList<>();
    }

    public List<Instruction> analyse() throws CompileError {
        analyseProgram();
        return instructions;
    }

    /**
     * 查看下一个 Token
     * 
     * @return peekedToken
     * @throws TokenizeError
     */
    private Token peek() throws TokenizeError {
        if (peekedToken == null) {
            peekedToken = tokenizer.nextToken();
        }
        return peekedToken;
    }

    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError
     */
    private Token next() throws TokenizeError {
        if (peekedToken != null) {
            var token = peekedToken;
            peekedToken = null;
            return token;
        } else {
            return tokenizer.nextToken();
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则返回 true
     * 
     * @param tt
     * @return
     * @throws TokenizeError
     */
    private boolean check(TokenType tt) throws TokenizeError {
        var token = peek();
        return token.getTokenType() == tt;
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回这个 token
     * 
     * @param tt 类型
     * @return 如果匹配则返回这个 token，否则返回 null
     * @throws TokenizeError
     */
    private Token nextIf(TokenType tt) throws TokenizeError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            return null;
        }
    }

    /**
     * 如果下一个 token 的类型是 tt，则前进一个 token 并返回，否则抛出异常
     * 
     * @param tt 类型
     * @return 这个 token
     * @throws CompileError 如果类型不匹配
     */
    private Token expect(TokenType tt) throws CompileError {
        var token = peek();
        if (token.getTokenType() == tt) {
            return next();
        } else {
            throw new ExpectedTokenError(tt, token);
        }
    }

    /**
     * 获取下一个变量的栈偏移
     * 
     * @return
     */
    private int getNextVariableOffset() {
        return this.nextOffset++;
    }

    /**
     * 添加一个符号
     * 
     * @param name          名字
     * @param isInitialized 是否已赋值
     * @param isConstant    是否是常量
     * @param curPos        当前 token 的位置（报错用）
     * @throws AnalyzeError 如果重复定义了则抛异常
     */
    private void addSymbol(String name, boolean isInitialized, boolean isConstant, Pos curPos) throws AnalyzeError {
        if (this.symbolTable.get(name) != null) {
            throw new AnalyzeError(ErrorCode.DuplicateDeclaration, curPos);
        } else {
            this.symbolTable.put(name, new SymbolEntry(isConstant, isInitialized, getNextVariableOffset()));
        }
    }

    /**
     * 设置符号为已赋值
     * 
     * @param name   符号名称
     * @param curPos 当前位置（报错用）
     * @throws AnalyzeError 如果未定义则抛异常
     */
    private void declareSymbol(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            entry.setInitialized(true);
        }
    }

    /**
     * 获取变量在栈上的偏移
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 栈偏移
     * @throws AnalyzeError
     */
    private int getOffset(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.getStackOffset();
        }
    }

    /**
     * 获取变量是否是常量
     * 
     * @param name   符号名
     * @param curPos 当前位置（报错用）
     * @return 是否为常量
     * @throws AnalyzeError
     */
    private boolean isConstant(String name, Pos curPos) throws AnalyzeError {
        var entry = this.symbolTable.get(name);
        if (entry == null) {
            throw new AnalyzeError(ErrorCode.NotDeclared, curPos);
        } else {
            return entry.isConstant();
        }
    }

    /**
     * <程序> ::= 'begin'<主过程>'end'
     */
    private void analyseProgram() throws CompileError {
        // 示例函数，示例如何调用子程序
        // 'begin'
        expect(TokenType.Begin);

        analyseMain();

        // 'end'
        expect(TokenType.End);
        expect(TokenType.EOF);
    }

    /**
     * <主过程> ::= <常量声明><变量声明><语句序列>
     */
    private void analyseMain() throws CompileError {
        // 常量分析
        analyseConstantDeclaration();
        // 变量分析
        analyseVariableDeclaration();
        // 语句序列分析
        analyseStatementSequence();
    }

    /**
     * <常量声明语句> ::= 'const'<标识符>'='<常表达式>';'
     */
    private void analyseConstantDeclaration() throws CompileError {
        // 如果下一个 token 是 const 就继续
        while (nextIf(TokenType.Const) != null) {
            // 变量名
            var nameToken = expect(TokenType.Ident);
            if (symbolTable.containsKey(nameToken.getValue())) {
                throw new AnalyzeError(ErrorCode.DuplicateDeclaration, nameToken.getStartPos());
            }
            // 等于号
            expect(TokenType.Equal);
            // 常表达式
            var value = analyseConstantExpression();
            // 分号
            expect(TokenType.Semicolon);
            // 添加符号到符号表
            addSymbol(String.valueOf(nameToken.getValue()), true, true, nameToken.getStartPos());
            instructions.add(new Instruction(Operation.LIT, value));
        }
    }

    /**
     * <变量声明语句> ::= 'var'<标识符>['='<表达式>]';'
     */
    private void analyseVariableDeclaration() throws CompileError {
        // 如果下一个 token 是 const 就继续
        while (nextIf(TokenType.Var) != null) {
            // 变量名
            var nameToken = expect(TokenType.Ident);
            if (symbolTable.containsKey(nameToken.getValue())) {
                throw new AnalyzeError(ErrorCode.DuplicateDeclaration, nameToken.getStartPos());
            }
            // 等于号
            if(check(TokenType.Equal)) {
                // 表达式
                analyseExpression();
                // 分号
                expect(TokenType.Semicolon);
                addSymbol(String.valueOf(nameToken.getValue()), true, false, nameToken.getStartPos());
                continue;
            }
            // 分号
            expect(TokenType.Semicolon);
            // 添加符号到符号表
            addSymbol(nameToken.getValueString(), false, false, nameToken.getStartPos());
        }
    }

    /**
     * <语句序列> ::= {<语句>}
     */
    private void analyseStatementSequence() throws CompileError {
        while(check(TokenType.Ident)||check(TokenType.Print)||check(TokenType.Semicolon)) {
            analyseStatement();
        }
    }

    /**
     * <语句> ::= <赋值语句>|<输出语句>|<空语句>
     */
    private void analyseStatement() throws CompileError {
        if(check(TokenType.Ident)) {
            // 赋值分析
            analyseAssignmentStatement();
        }
        else if(check(TokenType.Print)) {
            // 输出分析
            analyseOutputStatement();
        }
        else if(check(TokenType.Semicolon)) {
            // 空分析
            next();
        } else {
            throw new AnalyzeError(ErrorCode.InvalidInput, next().getStartPos());
        }
//        else{
//            // 都不是，摸了
//            throw new ExpectedTokenError(List.of(TokenType.Ident, TokenType.Uint, TokenType.LParen), next());
//        }
    }

    /**
     * <常表达式> ::= [<符号>]<无符号整数>
     */
    private int analyseConstantExpression() throws CompileError {
        boolean negative = false;
        if (nextIf(TokenType.Plus) != null) {
            negative = false;
        } else if (nextIf(TokenType.Minus) != null) {
            negative = true;
        }

        var token = expect(TokenType.Uint);

        int value = (int) token.getValue();
        if (negative) {
            value = -value;
        }

        return value;
    }

    /**
     * <赋值语句> ::= <标识符>'='<表达式>';'
     */
    private void analyseAssignmentStatement() throws CompileError {
        //标识符
        var nameToken = expect(TokenType.Ident);
        if ( ! symbolTable.containsKey(nameToken.getValue())) {
            throw new AnalyzeError(ErrorCode.NotDeclared, nameToken.getStartPos());
        } else if (isConstant(nameToken.getValueString(), nameToken.getStartPos())) {
            throw new AnalyzeError(ErrorCode.AssignToConstant ,nameToken.getStartPos());
        }
        // 等号
        expect(TokenType.Equal);
        // 表达式
        analyseExpression();
        // 分号
        expect(TokenType.Semicolon);


        if(symbolTable.get(nameToken.getValue()).isInitialized()){
            instructions.add(new Instruction(Operation.STO, getOffset(nameToken.getValueString(), nameToken.getStartPos())));
        } else {
            declareSymbol(nameToken.getValueString(),nameToken.getStartPos());
        }
    }

    /**
     * <表达式> ::= <项>{<加法型运算符><项>}
     */
    private void analyseExpression() throws CompileError {
        //项分析
        analyseTerm();

        while(check(TokenType.Minus)||check(TokenType.Plus)) {
            Token token = next();
            analyseTerm();
            if (token.getTokenType() == TokenType.Minus) {
                instructions.add(new Instruction(Operation.SUB));
            } else if (token.getTokenType() == TokenType.Plus) {
                instructions.add(new Instruction(Operation.ADD));
            }
        }
    }

    /**
     * <输出语句> ::= 'print' '(' <表达式> ')' ';'
     */
    private void analyseOutputStatement() throws CompileError {
        expect(TokenType.Print);
        expect(TokenType.LParen);
        analyseExpression();
        expect(TokenType.RParen);
        expect(TokenType.Semicolon);
        instructions.add(new Instruction(Operation.WRT));
    }

    /**
     * <项> ::= <因子>{<乘法型运算符><因子>}
     */
    private void analyseTerm() throws CompileError {
        //因子分析
        analyseFactor();
        while (check(TokenType.Mult) || check(TokenType.Div)) {
            Token op = next();
            analyseFactor();
            if (op.getTokenType() == TokenType.Mult) {
                instructions.add(new Instruction(Operation.MUL));
            } else if (op.getTokenType() == TokenType.Div) {
                instructions.add(new Instruction(Operation.DIV));
            }
        }
    }

    /**
     * <因子> ::= [<符号>]( <标识符> | <无符号整数> | '('<表达式>')' )
     */
    private void analyseFactor() throws CompileError {
        boolean negate;
        if (nextIf(TokenType.Minus) != null) {
            negate = true;
            // 计算结果需要被 0 减
            instructions.add(new Instruction(Operation.LIT, 0));
        } else {
            nextIf(TokenType.Plus);
            negate = false;
        }

        if (check(TokenType.Ident)) {
            var nameToken = next();
            String key = nameToken.getValueString();
            if(!symbolTable.containsKey(key)) {
                //没定义，摸了
                throw new AnalyzeError(ErrorCode.NotDeclared, nameToken.getStartPos());
            } else if(!symbolTable.get(key).isInitialized()) {
                //没赋值，摸了
                throw new AnalyzeError(ErrorCode.NotInitialized, nameToken.getStartPos());
            }
            var offset = getOffset(key,nameToken.getStartPos());
            instructions.add(new Instruction(Operation.LOD,offset));
        } else if (check(TokenType.Uint)) {
            var nameToken = next();
            int value = Integer.parseInt(nameToken.getValueString());
            instructions.add(new Instruction(Operation.LIT, value));
        } else if (check(TokenType.LParen)) {
            // 表达式分析
            next();
            analyseExpression();
            expect(TokenType.RParen);
        } else {
            // 都不是，摸了
            throw new ExpectedTokenError(List.of(TokenType.Ident, TokenType.Uint, TokenType.LParen), next());
        }

        if (negate) {
            instructions.add(new Instruction(Operation.SUB));
        }
    }
}
