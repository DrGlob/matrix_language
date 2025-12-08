package org.example.parser

class Lexer(private val source: String) {
    private val tokens = mutableListOf<Token>()
    private var start = 0
    private var current = 0
    private var line = 1
    private var column = 1

    private val keywords = mapOf(
        "let" to TokenType.LET,
        "if" to TokenType.IF,
        "else" to TokenType.ELSE,
        "for" to TokenType.FOR,
        "in" to TokenType.IN,
        "function" to TokenType.FUNCTION,
        "return" to TokenType.RETURN,
        "print" to TokenType.PRINT,
        "zeros" to TokenType.ZEROS,
        "ones" to TokenType.ONES,
        "identity" to TokenType.IDENTITY,
        "transpose" to TokenType.TRANSPOSE,
        "rows" to TokenType.ROWS,
        "cols" to TokenType.COLS,
        // Функциональные ключевые слова
        "lambda" to TokenType.LAMBDA,
        "fn" to TokenType.FN,
        "compose" to TokenType.COMPOSE,
        "pipe" to TokenType.PIPE,

        // Функции высшего порядка
        "map" to TokenType.MAP,
        "reduce" to TokenType.REDUCE,
        "filter" to TokenType.FILTER
    )

    fun scanTokens(): List<Token> {
        while (!isAtEnd()) {
            start = current
            scanToken()
        }

        tokens.add(Token(TokenType.EOF, "", null, line, column))
        return tokens
    }

    private fun scanToken() {
        val c = advance()
        when (c) {
            '(' -> addToken(TokenType.LPAREN)
            ')' -> addToken(TokenType.RPAREN)
            '{' -> addToken(TokenType.LBRACE)
            '}' -> addToken(TokenType.RBRACE)
            '[' -> addToken(TokenType.LBRACKET)
            ']' -> addToken(TokenType.RBRACKET)
            ',' -> addToken(TokenType.COMMA)
            ';' -> addToken(TokenType.SEMICOLON)
            '.' -> {
                if (match('.')) {
                    addToken(TokenType.DOTDOT)
                } else {
                    addToken(TokenType.DOT)
                }
            }
            '+' -> addToken(TokenType.PLUS)
            '-' -> addToken(TokenType.MINUS)
            '*' -> addToken(TokenType.MULTIPLY)
            '=' -> addToken(if (match('=')) TokenType.EQ else TokenType.ASSIGN)
            '!' -> addToken(if (match('=')) TokenType.NEQ else throw error("Unexpected character '$c'"))
            '<' -> addToken(if (match('=')) TokenType.LTE else TokenType.LT)
            '>' -> addToken(if (match('=')) TokenType.GTE else TokenType.GT)

            '/' -> {
                if (match('/')) {
                    // Однострочный комментарий
                    while (peek() != '\n' && !isAtEnd()) advance()
                } else if (match('*')) {
                    // Многострочный комментарий
                    while (!(peek() == '*' && peekNext() == '/') && !isAtEnd()) {
                        if (peek() == '\n') line++
                        advance()
                    }
                    if (!isAtEnd()) {
                        advance() // *
                        advance() // /
                    }
                } else {
                    addToken(TokenType.DIVIDE)
                }
            }

            // Игнорируем пробелы и табуляции
            ' ', '\r', '\t' -> { /* игнорируем */ }
            '\n' -> {
                line++
                column = 1
            }

            '"' -> string()

            else -> when {
                isDigit(c) -> number()
                isAlpha(c) -> identifier()
                else -> {
                    // Пропускаем не-ASCII символы с предупреждением
                    if (c.code > 127) {
                        System.err.println("Warning: Skipping non-ASCII character '$c' at line $line, column $column")
                        // Пропускаем этот символ и продолжаем
                    } else {
                        throw error("Unexpected character '$c' (code: ${c.code})")
                    }
                }
            }
        }
    }

    private fun identifier() {
        while (isAlphaNumeric(peek())) advance()

        val text = source.substring(start, current)
        val type = keywords[text] ?: TokenType.IDENTIFIER
        addToken(type)
    }

    private fun number() {
        while (isDigit(peek())) advance()

        // Дробная часть
        if (peek() == '.' && isDigit(peekNext())) {
            advance()
            while (isDigit(peek())) advance()
        }

        val value = source.substring(start, current).toDouble()
        addToken(TokenType.NUMBER, value)
    }

    private fun string() {
        while (peek() != '"' && !isAtEnd()) {
            if (peek() == '\n') line++
            advance()
        }

        if (isAtEnd()) throw error("Unterminated string")

        // Закрывающая кавычка
        advance()

        val value = source.substring(start + 1, current - 1)
        addToken(TokenType.STRING, value)
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false
        current++
        column++
        return true
    }

    private fun advance(): Char {
        current++
        column++
        return source[current - 1]
    }

    private fun peek(): Char = if (isAtEnd()) '\u0000' else source[current]
    private fun peekNext(): Char = if (current + 1 >= source.length) '\u0000' else source[current + 1]
    private fun isAlpha(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z' || c == '_'
    private fun isDigit(c: Char): Boolean = c in '0'..'9'
    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)
    private fun isAtEnd(): Boolean = current >= source.length

    private fun addToken(type: TokenType, literal: Any? = null) {
        val text = source.substring(start, current)
        tokens.add(Token(type, text, literal, line, column))
    }

    private fun error(message: String): RuntimeException {
        return RuntimeException("[$line:$column] $message")
    }
}
