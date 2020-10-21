package miniplc0java.tokenizer;

import miniplc0java.error.TokenizeError;
import miniplc0java.error.ErrorCode;

public class Tokenizer {

    private final StringIter it;

    public Tokenizer(StringIter it) {
        this.it = it;
    }

    // 这里本来是想实现 Iterator<Token> 的，但是 Iterator 不允许抛异常，于是就这样了
    /**
     * 获取下一个 Token
     * 
     * @return
     * @throws TokenizeError 如果解析有异常则抛出
     */
    public Token nextToken() throws TokenizeError {
        it.readAll();

        // 跳过之前的所有空白字符
        skipSpaceCharacters();

        if (it.isEOF()) {
            return new Token(TokenType.EOF, "", it.currentPos(), it.currentPos());
        }

        char peek = it.peekChar();
        if (Character.isDigit(peek)) {
            return lexUInt();
        } else if (Character.isAlphabetic(peek)) {
            return lexIdentOrKeyword();
        } else {
            return lexOperatorOrUnknown();
        }
    }

    private Token lexUInt() throws TokenizeError {
        StringBuffer int_token = new StringBuffer();
        while(Character.isDigit(it.peekChar())) {
            int_token.append(it.nextChar());
        }
        String res_line = new String(String.valueOf(int_token));
    	return new Token(TokenType.Uint, Integer.parseInt(res_line), it.previousPos(), it.currentPos());
    }

    private Token lexIdentOrKeyword() throws TokenizeError {

        StringBuffer str_token = new StringBuffer();
    	while(Character.isDigit(it.peekChar())||Character.isAlphabetic(it.peekChar())) {
            str_token.append(it.nextChar());
        }

        String res_line = new String(String.valueOf(str_token));
        if(res_line.equals("begin"))
        	return new Token(TokenType.Begin, res_line, it.previousPos(), it.currentPos());
        
        if(res_line.equals("end"))
        	return new Token(TokenType.End, res_line, it.previousPos(), it.currentPos());
        
        if(res_line.equals("var"))
        	return new Token(TokenType.Var, res_line, it.previousPos(), it.currentPos());
        
        if(res_line.equals("const"))
        	return new Token(TokenType.Const, res_line, it.previousPos(), it.currentPos());
        
        if(res_line.equals("print"))
        	return new Token(TokenType.Print, res_line, it.previousPos(), it.currentPos());
        
    	return new Token(TokenType.Ident, res_line, it.previousPos(), it.currentPos());
    }

    private Token lexOperatorOrUnknown() throws TokenizeError {
        switch (it.nextChar()) {
            case '+':
                return new Token(TokenType.Plus, '+', it.previousPos(), it.currentPos());

            case '-':
            	return new Token(TokenType.Minus, '-', it.previousPos(), it.currentPos());

            case '*':
            	return new Token(TokenType.Mult, '*', it.previousPos(), it.currentPos());

            case '/':
            	return new Token(TokenType.Div, '/', it.previousPos(), it.currentPos());

            case '(':
                return new Token(TokenType.LParen, '(', it.previousPos(), it.currentPos());

            case ')':
            	return new Token(TokenType.RParen, ')', it.previousPos(), it.currentPos());

            case '=':
            	return new Token(TokenType.Equal, '=', it.previousPos(), it.currentPos());

            case ';':
            	return new Token(TokenType.Semicolon, ';', it.previousPos(), it.currentPos());

            default:
                // 不认识这个输入，摸了
                throw new TokenizeError(ErrorCode.InvalidInput, it.previousPos());
        }
    }

    private void skipSpaceCharacters() {
        while (!it.isEOF() && Character.isWhitespace(it.peekChar())) {
            it.nextChar();
        }
    }
}
