package org.example.parser

// Типы токенов

enum class TokenType {
    // Литералы
    NUMBER, STRING, IDENTIFIER,

    // Ключевые слова
    LET, IF, ELSE, FOR, IN, FUNCTION, RETURN, PRINT,

    // Функции
    ZEROS, ONES, IDENTITY, TRANSPOSE, ROWS, COLS,

//    LAMBDA, FN, COMPOSE, PIPE, ARROW,

//    MAP, REDUCE, FILTER,

    // Операторы
    PLUS, MINUS, MULTIPLY, DIVIDE, ASSIGN,
    EQ, NEQ, LT, GT, LTE, GTE,

    // Функциональные операторы
    PIPE_OP,  // |>
    COMPOSE_OP,  // .>
    LAMBDA_OP,  // \

    // Разделители
    COMMA, SEMICOLON, LPAREN, RPAREN, LBRACE, RBRACE,
    LBRACKET, RBRACKET, DOT, // Добавлен DOT

    // Прочие
    DOTDOT, // ..
    EOF
}

// Токен
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
    val column: Int
)

// Узлы AST
sealed class Expr {
    data class MatrixLiteral(val rows: List<List<Double>>) : Expr()
    data class NumberLiteral(val value: Double) : Expr()
    data class StringLiteral(val value: String) : Expr()
    data class Variable(val name: String) : Expr()
    data class Assign(val name: String, val value: Expr) : Expr()  // Добавлено
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class Call(val callee: Expr, val args: List<Expr>) : Expr()
    data class Function(val name: String, val params: List<String>, val body: Stmt) : Expr()
    data class Get(val obj: Expr, val name: String) : Expr()

    // Выражение-условие (if ... else ...) как результат
    data class Conditional(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr()

    // Функциональные выражения
    data class Lambda(val params: List<String>, val body: Expr) : Expr()
    data class Compose(val outer: Expr, val inner: Expr) : Expr()
    data class PartialApply(val callee: Expr, val appliedArgs: List<Expr>) : Expr()
}

sealed class Stmt {
    data class Expression(val expression: Expr) : Stmt()
    data class Print(val expression: Expr) : Stmt()
    data class Var(val name: String, val initializer: Expr?) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    data class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt()
    data class For(val variable: String, val rangeStart: Int, val rangeEnd: Int, val body: Stmt) : Stmt()
    data class Function(val name: String, val params: List<String>, val body: Stmt.Block) : Stmt()
    data class Return(val keyword: Token, val value: Expr?) : Stmt()
}
