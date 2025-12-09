package org.example.parser

import org.example.core.Matrix
import org.example.core.MatrixFactory
import kotlin.math.sqrt

// Исключения
class RuntimeError(val token: Token, message: String) : RuntimeException(message)
class ReturnException(val value: Any?) : Exception("Return statement")

class Interpreter {
    private val globals = Environment()
    private var environment = globals

    // Специальный объект для пустого возврата
    companion object {
        val UNIT = object : Any() {
            override fun toString(): String = "unit"
        }
    }

    init {
        // Встроенные функции - возвращают не-null значения
        globals.define("map", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val function = interpreter.expectCallable("map", arguments[1])
                return interpreter.applyMap(arguments[0], function)
            }
        })

        globals.define("reduce", object : MatrixCallable {
            override fun arity(): Int = 3
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val function = interpreter.expectCallable("reduce", arguments[2])
                return interpreter.applyReduce(arguments[0], arguments[1], function)
            }
        })

        globals.define("zip", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return interpreter.applyZip(arguments[0], arguments[1])
            }
        })

        globals.define("unzip", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return interpreter.applyUnzip(arguments[0])
            }
        })

        globals.define("filter", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val predicate = interpreter.expectCallable("filter", arguments[1])
                return interpreter.applyFilter(arguments[0], predicate)
            }
        })

        globals.define("compose", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val f = arguments[0] as MatrixCallable
                val g = arguments[1] as MatrixCallable

                return object : MatrixCallable {
                    override fun arity(): Int = 1
                    override fun call(interpreter: Interpreter, args: List<Any?>): Any? {
                        val intermediate = g.call(interpreter, args)
                        return f.call(interpreter, listOf(intermediate))
                    }
                }
            }
        })

        globals.define("zeros", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val rows = (arguments[0] as Double).toInt()
                val cols = (arguments[1] as Double).toInt()
                return MatrixFactory.zeros(rows, cols)
            }
            override fun toString(): String = "<native function zeros>"
        })

        globals.define("ones", object : MatrixCallable {
            override fun arity(): Int = 2
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val rows = (arguments[0] as Double).toInt()
                val cols = (arguments[1] as Double).toInt()
                return MatrixFactory.ones(rows, cols)
            }
            override fun toString(): String = "<native function ones>"
        })

        globals.define("identity", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                val size = (arguments[0] as Double).toInt()
                return MatrixFactory.identity(size)
            }
            override fun toString(): String = "<native function identity>"
        })

        globals.define("transpose", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return (arguments[0] as Matrix).transpose()
            }
            override fun toString(): String = "<native function transpose>"
        })

        globals.define("rows", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return (arguments[0] as Matrix).numRows.toDouble()
            }
            override fun toString(): String = "<native function rows>"
        })

        globals.define("cols", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                return (arguments[0] as Matrix).numCols.toDouble()
            }
            override fun toString(): String = "<native function cols>"
        })

        globals.define("norm", object : MatrixCallable {
            override fun arity(): Int = 1
            override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
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
            is Expr.MatrixLiteral -> Matrix(expr.rows)
            is Expr.NumberLiteral -> expr.value
            is Expr.StringLiteral -> expr.value
            is Expr.Variable -> environment.get(expr.name)
            is Expr.Assign -> {
                val value = evaluate(expr.value)
                environment.assign(expr.name, value)
                value
            }
            is Expr.IfExpr -> {
                val condition = evaluate(expr.condition)
                if (isTruthy(condition)) evaluate(expr.thenBranch) else evaluate(expr.elseBranch)
            }
            is Expr.LetInExpr -> {
                val bound = evaluate(expr.boundExpr)
                val previous = environment
                val scoped = Environment(environment)
                try {
                    scoped.define(expr.name, bound)
                    environment = scoped
                    evaluate(expr.bodyExpr)
                } finally {
                    environment = previous
                }
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
            is Expr.CallExpr -> evaluateCall(expr)
            is Expr.MethodCallExpr -> evaluateMethodCall(expr)
            is Expr.PropertyAccess -> evaluatePropertyAccess(expr)
            is Expr.Function -> MatrixFunction(expr, environment)
            is Expr.LambdaExpr -> {
                object : MatrixCallable {
                    override fun arity(): Int = expr.params.size
                    override fun call(interpreter: Interpreter, arguments: List<Any?>): Any? {
                        val environment = Environment(this@Interpreter.environment)

                        for ((i, param) in expr.params.withIndex()) {
                            environment.define(param, arguments[i])
                        }

                        val previousEnv = this@Interpreter.environment
                        try {
                            this@Interpreter.environment = environment
                            return evaluate(expr.body) ?: UNIT
                        } finally {
                            this@Interpreter.environment = previousEnv
                        }
                    }

                    override fun toString(): String =
                        "<lambda(${expr.params.joinToString(", ")})>"
                }
            }
        }
    }

    private fun evaluateCall(expr: Expr.CallExpr): Any? {
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

    private fun evaluateMethodCall(expr: Expr.MethodCallExpr): Any? {
        val receiver = evaluate(expr.receiver)
        val args = expr.args.map { evaluate(it) }

        return when (expr.method) {
            "map" -> {
                if (args.isEmpty()) {
                    throw RuntimeError(Token(TokenType.IDENTIFIER, expr.method, null, 0, 0),
                        "Method '${expr.method}' expects a lambda")
                }
                val fn = expectCallable("map", args.getOrNull(0))
                applyMap(receiver, fn)
            }
            "reduce" -> {
                if (args.size < 2) {
                    throw RuntimeError(Token(TokenType.IDENTIFIER, expr.method, null, 0, 0),
                        "Method '${expr.method}' expects initial value and lambda")
                }
                val initial = args.getOrNull(0)
                val fn = expectCallable("reduce", args.getOrNull(1))
                applyReduce(receiver, initial, fn)
            }
            "zip" -> {
                val other = args.getOrNull(0)
                    ?: throw RuntimeError(Token(TokenType.IDENTIFIER, expr.method, null, 0, 0),
                        "Method '${expr.method}' expects another collection")
                applyZip(receiver, other)
            }
            "unzip" -> applyUnzip(receiver)
            else -> {
                val dummyToken = Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                throw RuntimeError(dummyToken, "Unknown method '${expr.method}'")
            }
        }
    }

    private fun evaluatePropertyAccess(expr: Expr.PropertyAccess): Any? {
        val obj = evaluate(expr.receiver)
        return when (obj) {
            is Matrix -> {
                when (expr.name) {
                    "rows" -> obj.numRows.toDouble()
                    "cols" -> obj.numCols.toDouble()
                    "transpose" -> obj.transpose()
                    else -> throw RuntimeError(
                        Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                        "Undefined property '${expr.name}' on matrix"
                    )
                }
            }
            is Pair<*, *> -> {
                when (expr.name) {
                    "first" -> obj.first
                    "second" -> obj.second
                    else -> throw RuntimeError(
                        Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                        "Undefined property '${expr.name}' on pair"
                    )
                }
            }
            is List<*> -> {
                when (expr.name) {
                    "size" -> obj.size.toDouble()
                    else -> throw RuntimeError(
                        Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                        "Undefined property '${expr.name}' on list"
                    )
                }
            }
            else -> {
                val dummyToken = Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                throw RuntimeError(dummyToken, "Undefined property '${expr.name}'")
            }
        }
    }

    private fun expectCallable(name: String, value: Any?): MatrixCallable {
        return value as? MatrixCallable ?: throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0, 0),
            "Expected lambda/function as argument for '$name'"
        )
    }

    private fun checkArity(expected: Int, callable: MatrixCallable, name: String) {
        if (callable.arity() != expected) {
            throw RuntimeError(
                Token(TokenType.IDENTIFIER, name, null, 0, 0),
                "Expected function with $expected parameter(s) for '$name'"
            )
        }
    }

    private fun applyMap(target: Any?, function: MatrixCallable): Any {
        checkArity(1, function, "map")
        return when (target) {
            is Matrix -> {
                Matrix(target.rows.map { row ->
                    row.map { value ->
                        val result = function.call(this, listOf(value))
                        (result as? Double) ?: throw RuntimeError(
                            Token(TokenType.IDENTIFIER, "map", null, 0, 0),
                            "Matrix map expects lambda to return number"
                        )
                    }
                })
            }
            is List<*> -> target.map { function.call(this, listOf(it)) }
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "map", null, 0, 0),
                "Can only map over matrices or lists"
            )
        }
    }

    private fun applyReduce(target: Any?, initial: Any?, function: MatrixCallable): Any? {
        checkArity(2, function, "reduce")
        val items = flattenCollection(target, "reduce")
        var accumulator = initial
        for (value in items) {
            accumulator = function.call(this, listOf(accumulator, value))
        }
        return accumulator
    }

    private fun applyFilter(target: Any?, predicate: MatrixCallable): Any {
        checkArity(1, predicate, "filter")
        return when (target) {
            is Matrix -> {
                val filteredRows = target.rows.map { row ->
                    row.filter { value -> isTruthy(predicate.call(this, listOf(value))) }
                }.filter { it.isNotEmpty() }
                if (filteredRows.isEmpty()) return Matrix(emptyList())
                val width = filteredRows.first().size
                if (!filteredRows.all { it.size == width }) {
                    throw RuntimeError(
                        Token(TokenType.IDENTIFIER, "filter", null, 0, 0),
                        "Filter over matrix must keep consistent row sizes"
                    )
                }
                Matrix(filteredRows)
            }
            is List<*> -> target.filter { isTruthy(predicate.call(this, listOf(it))) }
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "filter", null, 0, 0),
                "Can only filter matrices or lists"
            )
        }
    }

    private fun applyZip(left: Any?, right: Any?): List<Pair<Any?, Any?>> {
        val leftItems = flattenCollection(left, "zip")
        val rightItems = flattenCollection(right, "zip")
        val size = minOf(leftItems.size, rightItems.size)
        return (0 until size).map { i -> Pair(leftItems[i], rightItems[i]) }
    }

    private fun applyUnzip(value: Any?): Pair<List<Any?>, List<Any?>> {
        val pairs = when (value) {
            is List<*> -> value
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "unzip", null, 0, 0),
                "unzip expects a list of pairs"
            )
        }

        val first = mutableListOf<Any?>()
        val second = mutableListOf<Any?>()
        for (pair in pairs) {
            if (pair !is Pair<*, *>) {
                throw RuntimeError(
                    Token(TokenType.IDENTIFIER, "unzip", null, 0, 0),
                    "unzip expects a list of pairs"
                )
            }
            first.add(pair.first)
            second.add(pair.second)
        }
        return Pair(first, second)
    }

    private fun flattenCollection(target: Any?, opName: String): List<Any?> {
        return when (target) {
            is Matrix -> target.rows.flatten()
            is List<*> -> target.toList()
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, opName, null, 0, 0),
                "$opName expects a matrix or a list"
            )
        }
    }

    private fun isTruthy(obj: Any?): Boolean {
        return when (obj) {
            null -> false
            is Boolean -> obj
            UNIT -> false
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
            UNIT -> "unit"
            is Double -> {
                val text = obj.toString()
                if (text.endsWith(".0")) {
                    text.substring(0, text.length - 2)
                } else {
                    "%.4f".format(obj)
                }
            }
            is Pair<*, *> -> "(${stringify(obj.first)}, ${stringify(obj.second)})"
            is List<*> -> obj.joinToString(prefix = "[", postfix = "]") { stringify(it) }
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
            return returnValue.value ?: Interpreter.UNIT
        }

        return Interpreter.UNIT
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
