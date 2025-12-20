package org.example.parser

/**
 * Типы токенов для лексера и парсера.
 */
enum class TokenType {
    // Литералы
    NUMBER, STRING, IDENTIFIER,

    // Ключевые слова
    LET, IF, THEN, ELSE, IN, FUNCTION, RETURN, PRINT,

    // Функции
    ZEROS, ONES, IDENTITY, TRANSPOSE, ROWS, COLS,

    // Операторы
    PLUS, MINUS, MULTIPLY, DIVIDE, ASSIGN,
    EQ, NEQ, LT, GT, LTE, GTE,

    // Функциональные операторы
    PIPE_OP,  // |>
    COMPOSE_OP,  // .>
    LAMBDA_OP,  // \
    ARROW, // ->

    // Разделители
    COMMA, SEMICOLON, LPAREN, RPAREN, LBRACE, RBRACE,
    LBRACKET, RBRACKET, DOT, // Добавлен DOT

    // Прочие
    DOTDOT, // ..
    EOF
}

/**
 * Лексический токен с позиционной информацией.
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val literal: Any?,
    val line: Int,
    val column: Int
)

/**
 * Узлы AST для выражений (expression-level).
 */
sealed class Expr {
    data class MatrixLiteral(val rows: List<List<Double>>) : Expr()
    data class NumberLiteral(val value: Double) : Expr()
    data class StringLiteral(val value: String) : Expr()
    data class Variable(val name: String) : Expr()
    data class Assign(val name: String, val value: Expr) : Expr()
    data class Binary(val left: Expr, val operator: Token, val right: Expr) : Expr()
    data class Unary(val operator: Token, val right: Expr) : Expr()
    data class IfExpr(val condition: Expr, val thenBranch: Expr, val elseBranch: Expr) : Expr()
    data class LetInExpr(val name: String, val boundExpr: Expr, val bodyExpr: Expr) : Expr()
    data class LambdaExpr(val params: List<String>, val body: Expr) : Expr()
    data class CallExpr(val callee: Expr, val args: List<Expr>) : Expr()
    data class MethodCallExpr(val receiver: Expr, val method: String, val args: List<Expr>) : Expr()
    data class PropertyAccess(val receiver: Expr, val name: String) : Expr()
    data class Function(val name: String, val params: List<String>, val body: Stmt) : Expr()
}

/**
 * Узлы AST для операторов/statement-level конструкций.
 */
sealed class Stmt {
    data class Expression(val expression: Expr) : Stmt()
    data class Print(val expression: Expr) : Stmt()
    data class Var(val name: String, val initializer: Expr?) : Stmt()
    data class Block(val statements: List<Stmt>) : Stmt()
    data class If(val condition: Expr, val thenBranch: Stmt, val elseBranch: Stmt?) : Stmt()
    data class Function(val name: String, val params: List<String>, val body: Stmt.Block) : Stmt()
    data class Return(val keyword: Token, val value: Expr?) : Stmt()
}
