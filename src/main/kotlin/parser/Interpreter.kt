package org.example.parser

import org.example.core.Matrix
import org.example.core.MatrixFactory
import parser.Expr
import parser.Stmt
import parser.Token
import parser.TokenType
import kotlin.math.sqrt

// Исключения
class RuntimeError(val token: Token, message: String) : RuntimeException(message)
class ReturnException(val value: Any?) : Exception("Return statement")

class Interpreter {
    private val globals = Environment()
    private var environment = globals

    init {
        // Встроенные функции
        globals.define("zeros", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                val rows = (arguments[0] as Double).toInt()
                val cols = (arguments[1] as Double).toInt()
                return MatrixFactory.zeros(rows, cols)
            }
            override fun toString(): String = "<native function zeros>"
        })

        globals.define("ones", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                val rows = (arguments[0] as Double).toInt()
                val cols = (arguments[1] as Double).toInt()
                return MatrixFactory.ones(rows, cols)
            }
            override fun toString(): String = "<native function ones>"
        })

        globals.define("identity", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                val size = (arguments[0] as Double).toInt()
                return MatrixFactory.identity(size)
            }
            override fun toString(): String = "<native function identity>"
        })

        globals.define("transpose", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                return (arguments[0] as Matrix).transpose()
            }
            override fun toString(): String = "<native function transpose>"
        })

        globals.define("rows", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                return (arguments[0] as Matrix).numRows.toDouble()
            }
            override fun toString(): String = "<native function rows>"
        })

        globals.define("cols", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                return (arguments[0] as Matrix).numCols.toDouble()
            }
            override fun toString(): String = "<native function cols>"
        })

        globals.define("norm", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any {
                val matrix = arguments[0] as Matrix
                val sum = matrix.rows.flatten().sumOf { it * it }
                return sqrt(sum)
            }
            override fun toString(): String = "<native function norm>"
        })
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: RuntimeError) {
            println("Runtime error: ${error.message}")
        } catch (error: ReturnException) {
            // ReturnException не должен выбрасываться на верхнем уровне
            println("Warning: return statement outside function")
        } catch (error: Exception) {
            println("Error: ${error.message}")
        }
    }

    private fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.Expression -> evaluate(stmt.expression)
            is Stmt.Print -> {
                val value = evaluate(stmt.expression)
                println(stringify(value))
            }
            is Stmt.Var -> {
                val value = stmt.initializer?.let { evaluate(it) }
                environment.define(stmt.name, value)
            }
            is Stmt.Block -> {
                executeBlock(stmt.statements, Environment(environment))
            }
            is Stmt.If -> {
                val condition = evaluate(stmt.condition)
                if (isTruthy(condition)) {
                    execute(stmt.thenBranch)
                } else if (stmt.elseBranch != null) {
                    execute(stmt.elseBranch)
                }
            }
            is Stmt.For -> {
                for (i in stmt.rangeStart until stmt.rangeEnd) {
                    environment.define(stmt.variable, i.toDouble())
                    execute(stmt.body)
                }
            }
            is Stmt.Function -> {
                val function = MatrixFunction(stmt, environment)
                environment.define(stmt.name, function)
            }
            is Stmt.Return -> {
                val value = stmt.value?.let { evaluate(it) }
                throw ReturnException(value)
            }
        }
    }

    fun executeBlock(statements: List<Stmt>, newEnvironment: Environment) {
        val previous = this.environment
        try {
            this.environment = newEnvironment
            for (statement in statements) {
                execute(statement)
            }
        } finally {
            this.environment = previous
        }
    }

    private fun evaluate(expr: Expr): Any? {
        return when (expr) {
            is Expr.MatrixLiteral -> {
                Matrix(expr.rows)
            }
            is Expr.NumberLiteral -> expr.value
            is Expr.StringLiteral -> expr.value
            is Expr.Variable -> environment.get(expr.name)
            is Expr.Assign -> {
                val value = evaluate(expr.value)
                environment.assign(expr.name, value)
                value
            }
            is Expr.Binary -> {
                val left = evaluate(expr.left)
                val right = evaluate(expr.right)

                when (expr.operator.type) {
                    TokenType.PLUS -> when {
                        left is Matrix && right is Matrix -> left + right
                        left is Double && right is Double -> left + right
                        left is Double && right is Matrix -> right * left
                        left is Matrix && right is Double -> left * right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
                    }
                    TokenType.MINUS -> when {
                        left is Matrix && right is Matrix -> left - right
                        left is Double && right is Double -> left - right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
                    }
                    TokenType.MULTIPLY -> when {
                        left is Matrix && right is Matrix -> left * right
                        left is Double && right is Matrix -> right * left
                        left is Matrix && right is Double -> left * right
                        left is Double && right is Double -> left * right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
                    }
                    TokenType.DIVIDE -> when {
                        left is Double && right is Double -> left / right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers")
                    }
                    TokenType.EQ -> isEqual(left, right)
                    TokenType.NEQ -> !isEqual(left, right)
                    TokenType.LT -> when {
                        left is Double && right is Double -> left < right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers")
                    }
                    TokenType.GT -> when {
                        left is Double && right is Double -> left > right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers")
                    }
                    TokenType.LTE -> when {
                        left is Double && right is Double -> left <= right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers")
                    }
                    TokenType.GTE -> when {
                        left is Double && right is Double -> left >= right
                        else -> throw RuntimeError(expr.operator, "Operands must be numbers")
                    }
                    else -> throw RuntimeError(expr.operator, "Unknown operator")
                }
            }
            is Expr.Unary -> {
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.MINUS -> {
                        checkNumberOperand(expr.operator, right)
                        -(right as Double)
                    }
                    else -> throw RuntimeError(expr.operator, "Unknown operator")
                }
            }
            is Expr.Call -> {
                val callee = evaluate(expr.callee)
                val arguments = expr.args.map { evaluate(it) }

                if (callee is MatrixCallable) {
                    if (arguments.size != callee.arity()) {
                        val dummyToken = Token(TokenType.IDENTIFIER, "function_call", null, 0, 0)
                        throw RuntimeError(dummyToken,
                            "Expected ${callee.arity()} arguments but got ${arguments.size}")
                    }
                    return callee.call(this, arguments)
                }

                val dummyToken = Token(TokenType.IDENTIFIER, "function_call", null, 0, 0)
                throw RuntimeError(dummyToken, "Can only call functions")
            }
            is Expr.Get -> {
                val obj = evaluate(expr.obj)
                if (obj is Matrix) {
                    when (expr.name) {
                        "rows" -> obj.numRows.toDouble()
                        "cols" -> obj.numCols.toDouble()
                        "transpose" -> obj.transpose()
                        else -> {
                            val dummyToken = Token(TokenType.IDENTIFIER, "property_access", null, 0, 0)
                            throw RuntimeError(dummyToken, "Undefined property '${expr.name}'")
                        }
                    }
                } else {
                    val dummyToken = Token(TokenType.IDENTIFIER, "property_access", null, 0, 0)
                    throw RuntimeError(dummyToken, "Only matrices have properties")
                }
            }
            is Expr.Function -> {
                MatrixFunction(expr, environment)
            }
        }
    }

    private fun isTruthy(obj: Any?): Boolean {
        return when (obj) {
            null -> false
            is Boolean -> obj
            else -> true
        }
    }

    private fun isEqual(a: Any?, b: Any?): Boolean {
        return a == b
    }

    private fun checkNumberOperand(operator: Token, operand: Any?) {
        if (operand is Double) return
        throw RuntimeError(operator, "Operand must be a number")
    }

    private fun stringify(obj: Any?): String {
        return when (obj) {
            null -> "nil"
            is Double -> {
                val text = obj.toString()
                if (text.endsWith(".0")) {
                    text.substring(0, text.length - 2)
                } else {
                    "%.4f".format(obj)
                }
            }
            is Matrix -> {
                if (obj.numRows <= 5 && obj.numCols <= 5) {
                    obj.rows.joinToString("\n") { row ->
                        "[${row.joinToString(", ") { "%.2f".format(it) }}]"
                    }
                } else {
                    "Matrix(${obj.numRows}x${obj.numCols})"
                }
            }
            else -> obj.toString()
        }
    }
}

interface MatrixCallable {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Any?>): Any?
}

class MatrixFunction(
    private val declaration: Any,
    private val closure: Environment
) : MatrixCallable {
    override fun arity(): Int {
        return when (declaration) {
            is Stmt.Function -> declaration.params.size
            is Expr.Function -> declaration.params.size
            else -> 0
        }
    }

    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
        val environment = Environment(closure)

        val params = when (declaration) {
            is Stmt.Function -> declaration.params
            is Expr.Function -> declaration.params
            else -> emptyList()
        }

        for ((i, param) in params.withIndex()) {
            environment.define(param, arguments[i])
        }

        try {
            when (declaration) {
                is Stmt.Function -> interpreter.executeBlock(
                    listOf(declaration.body), environment
                )
                is Expr.Function -> interpreter.executeBlock(
                    listOf(declaration.body), environment
                )
            }
        } catch (returnValue: ReturnException) {
            return returnValue.value
        }

        return null
    }

    override fun toString(): String = "<function>"
}

class Environment(private val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Any?>()

    fun define(name: String, value: Any?) {
        values[name] = value
    }

    fun get(name: String): Any? {
        return when {
            values.containsKey(name) -> values[name]
            enclosing != null -> enclosing.get(name)
            else -> throw RuntimeException("Undefined variable '$name'")
        }
    }

    fun assign(name: String, value: Any?) {
        when {
            values.containsKey(name) -> values[name] = value
            enclosing != null -> enclosing.assign(name, value)
            else -> throw RuntimeException("Undefined variable '$name'")
        }
    }
}