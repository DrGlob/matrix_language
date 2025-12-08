package org.example.parser

import org.example.core.Matrix
import org.example.core.MatrixFactory

class Parser(private val tokens: List<Token>) {
    private var current = 0

    fun parse(): List<Stmt> {
        val statements = mutableListOf<Stmt>()
        while (!isAtEnd()) {
            statements.add(declaration())
        }
        return statements
    }

    private fun declaration(): Stmt {
        return when {
            match(TokenType.FUNCTION) -> functionDeclaration("function")
            match(TokenType.LET) -> varDeclaration()
            else -> statement()
        }
    }

    private fun functionDeclaration(kind: String): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect $kind name").lexeme
        consume(TokenType.LPAREN, "Expect '(' after $kind name")

        val parameters = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters")
                }
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name").lexeme)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expect ')' after parameters")
        consume(TokenType.LBRACE, "Expect '{' before $kind body")
        val body = block()
        return Stmt.Function(name, parameters, body)
    }

    private fun varDeclaration(): Stmt {
        val name = consume(TokenType.IDENTIFIER, "Expect variable name")
        val initializer = if (match(TokenType.ASSIGN)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after variable declaration")
        return Stmt.Var(name.lexeme, initializer)
    }

    private fun statement(): Stmt {
        return when {
            match(TokenType.PRINT) -> printStatement()
            match(TokenType.IF) -> ifStatement()
            match(TokenType.FOR) -> forStatement()
            match(TokenType.RETURN) -> returnStatement()
            match(TokenType.LBRACE) -> block()
            else -> expressionStatement()
        }
    }

    private fun printStatement(): Stmt {
        val value = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after value")
        return Stmt.Print(value)
    }

    private fun ifStatement(): Stmt {
        consume(TokenType.LPAREN, "Expect '(' after 'if'")
        val condition = expression()
        consume(TokenType.RPAREN, "Expect ')' after condition")

        val thenBranch = statement()
        val elseBranch = if (match(TokenType.ELSE)) statement() else null

        return Stmt.If(condition, thenBranch, elseBranch)
    }

    private fun forStatement(): Stmt {
        consume(TokenType.LPAREN, "Expect '(' after 'for'")
        val variable = consume(TokenType.IDENTIFIER, "Expect variable name").lexeme
        consume(TokenType.IN, "Expect 'in' after variable")

        val start = consume(TokenType.NUMBER, "Expect start number").literal as Double
        val rangeStart = start.toInt()

        consume(TokenType.DOTDOT, "Expect '..' in range")

        val end = consume(TokenType.NUMBER, "Expect end number").literal as Double
        val rangeEnd = end.toInt()

        consume(TokenType.RPAREN, "Expect ')' after for clause")
        consume(TokenType.LBRACE, "Expect '{' before for body")

        val body = block()
        return Stmt.For(variable, rangeStart, rangeEnd, body)
    }

    private fun returnStatement(): Stmt {
        val keyword = previous()
        val value = if (!check(TokenType.SEMICOLON)) expression() else null
        consume(TokenType.SEMICOLON, "Expect ';' after return value")
        return Stmt.Return(keyword, value)
    }

    private fun block(): Stmt.Block {
        val statements = mutableListOf<Stmt>()
        while (!check(TokenType.RBRACE) && !isAtEnd()) {
            statements.add(declaration())
        }
        consume(TokenType.RBRACE, "Expect '}' after block")
        return Stmt.Block(statements)
    }

    private fun expressionStatement(): Stmt {
        val expr = expression()
        consume(TokenType.SEMICOLON, "Expect ';' after expression")
        return Stmt.Expression(expr)
    }

    private fun expression(): Expr = assignment()

    private fun assignment(): Expr {
        val expr = equality()

        if (match(TokenType.ASSIGN)) {
            val equals = previous()
            val value = assignment()

            if (expr is Expr.Variable) {
                val name = expr.name
                return Expr.Assign(name, value)
            }

            error(equals, "Invalid assignment target")
        }

        return expr
    }

    private fun equality(): Expr {
        var expr = comparison()

        while (match(TokenType.EQ, TokenType.NEQ)) {
            val operator = previous()
            val right = comparison()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun comparison(): Expr {
        var expr = term()

        while (match(TokenType.LT, TokenType.GT, TokenType.LTE, TokenType.GTE)) {
            val operator = previous()
            val right = term()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun term(): Expr {
        var expr = factor()

        while (match(TokenType.PLUS, TokenType.MINUS)) {
            val operator = previous()
            val right = factor()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun factor(): Expr {
        var expr = unary()

        while (match(TokenType.MULTIPLY, TokenType.DIVIDE)) {
            val operator = previous()
            val right = unary()
            expr = Expr.Binary(expr, operator, right)
        }

        return expr
    }

    private fun unary(): Expr {
        if (match(TokenType.MINUS)) {
            val operator = previous()
            val right = unary()
            return Expr.Unary(operator, right)
        }

        return call()
    }

    private fun call(): Expr {
        var expr = primary()

        while (true) {
            if (match(TokenType.LPAREN)) {
                expr = finishCall(expr)
            } else if (match(TokenType.DOT)) {
                val name = consume(TokenType.IDENTIFIER, "Expect property name after '.'")
                expr = Expr.Get(expr, name.lexeme)
            } else {
                break
            }
        }

        return expr
    }

    private fun finishCall(callee: Expr): Expr {
        val arguments = mutableListOf<Expr>()
        if (!check(TokenType.RPAREN)) {
            do {
                if (arguments.size >= 255) {
                    error(peek(), "Can't have more than 255 arguments")
                }
                arguments.add(expression())
            } while (match(TokenType.COMMA))
        }

        val paren = consume(TokenType.RPAREN, "Expect ')' after arguments")
        return Expr.Call(callee, arguments)
    }

    private fun primary(): Expr {
        return when {
            match(TokenType.NUMBER) -> Expr.NumberLiteral(previous().literal as Double)
            match(TokenType.STRING) -> Expr.StringLiteral(previous().literal as String)
            match(TokenType.IDENTIFIER) -> Expr.Variable(previous().lexeme)
            match(TokenType.LBRACKET) -> matrixLiteral()
            match(TokenType.FUNCTION) -> functionExpression()
            match(TokenType.LPAREN) -> {
                val expr = expression()
                consume(TokenType.RPAREN, "Expect ')' after expression")
                expr
            }
            else -> throw error(peek(), "Expect expression")
        }
    }

    // ДОБАВЛЕНО: Функциональное выражение (анонимная функция)
    private fun functionExpression(): Expr {
        consume(TokenType.LPAREN, "Expect '(' after function")

        val parameters = mutableListOf<String>()
        if (!check(TokenType.RPAREN)) {
            do {
                if (parameters.size >= 255) {
                    error(peek(), "Can't have more than 255 parameters")
                }
                parameters.add(consume(TokenType.IDENTIFIER, "Expect parameter name").lexeme)
            } while (match(TokenType.COMMA))
        }
        consume(TokenType.RPAREN, "Expect ')' after parameters")

        consume(TokenType.LBRACE, "Expect '{' before function body")
        val body = block()

        // Передаем пустое имя для анонимной функции
        return Expr.Function("", parameters, body)
    }

    private fun matrixLiteral(): Expr {
        val rows = mutableListOf<List<Double>>()

        if (!check(TokenType.RBRACKET)) {
            do {
                rows.add(rowLiteral())
            } while (match(TokenType.COMMA) && !check(TokenType.RBRACKET))
        }

        consume(TokenType.RBRACKET, "Expect ']' after matrix")
        return Expr.MatrixLiteral(rows)
    }

    private fun rowLiteral(): List<Double> {
        val row = mutableListOf<Double>()

        if (match(TokenType.LBRACKET)) {
            do {
                if (check(TokenType.NUMBER)) {
                    row.add((advance().literal as Double))
                } else {
                    throw error(peek(), "Expect number in matrix row")
                }
            } while (match(TokenType.COMMA))

            consume(TokenType.RBRACKET, "Expect ']' after row")
        } else {
            if (check(TokenType.NUMBER)) {
                row.add((advance().literal as Double))
            }
        }

        return row
    }

    // Вспомогательные методы парсера
    private fun match(vararg types: TokenType): Boolean {
        for (type in types) {
            if (check(type)) {
                advance()
                return true
            }
        }
        return false
    }

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF
    private fun peek(): Token = tokens[current]
    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()
        throw error(peek(), message)
    }

    private fun error(token: Token, message: String): RuntimeException {
        return RuntimeException("[${token.line}:${token.column}] $message")
    }
}