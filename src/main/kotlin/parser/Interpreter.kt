package org.example.parser

import org.example.core.Matrix
import org.example.core.MatrixFactory
import kotlin.math.sqrt

// Исключения
class RuntimeError(val token: Token, message: String) : RuntimeException(message)
class ReturnException(val value: Value) : RuntimeException("Return statement")

class Interpreter {
    internal val globals = Environment()
    internal var environment: Environment = globals

    init {
        defineNative("map", 2) { args ->
            val fn = expectCallable("map", args[1])
            applyMap(args[0], fn)
        }
        defineNative("reduce", 3) { args ->
            val fn = expectCallable("reduce", args[2])
            applyReduce(args[0], args[1], fn)
        }
        defineNative("zip", 2) { args -> applyZip(args[0], args[1]) }
        defineNative("unzip", 1) { args -> applyUnzip(args[0]) }
        defineNative("filter", 2) { args ->
            val predicate = expectCallable("filter", args[1])
            applyFilter(args[0], predicate)
        }
        defineNative("compose", 2) { args ->
            val f = expectCallable("compose", args[0])
            val g = expectCallable("compose", args[1])
            NativeFunctionValue(g.arity()) { callArgs ->
                val intermediate = g.call(this, callArgs)
                f.call(this, listOf(intermediate))
            }
        }
        defineNative("zeros", 2) { args ->
            val rows = expectNumber("zeros", args[0]).toInt()
            val cols = expectNumber("zeros", args[1]).toInt()
            MatrixValue(MatrixFactory.zeros(rows, cols))
        }
        defineNative("ones", 2) { args ->
            val rows = expectNumber("ones", args[0]).toInt()
            val cols = expectNumber("ones", args[1]).toInt()
            MatrixValue(MatrixFactory.ones(rows, cols))
        }
        defineNative("identity", 1) { args ->
            val size = expectNumber("identity", args[0]).toInt()
            MatrixValue(MatrixFactory.identity(size))
        }
        defineNative("transpose", 1) { args ->
            MatrixValue(expectMatrix("transpose", args[0]).transpose())
        }
        defineNative("rows", 1) { args ->
            NumberValue(expectMatrix("rows", args[0]).numRows.toDouble())
        }
        defineNative("cols", 1) { args ->
            NumberValue(expectMatrix("cols", args[0]).numCols.toDouble())
        }
        defineNative("norm", 1) { args ->
            val matrix = expectMatrix("norm", args[0])
            val sum = matrix.rows.flatten().sumOf { it * it }
            NumberValue(sqrt(sum))
        }
    }

    private fun defineNative(name: String, arity: Int, impl: (List<Value>) -> Value) {
        globals.define(name, NativeFunctionValue(arity, impl))
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

    fun execute(stmt: Stmt) {
        when (stmt) {
            is Stmt.Expression -> evaluate(stmt.expression)
            is Stmt.Print -> {
                val value = evaluate(stmt.expression)
                println(stringify(value))
            }
            is Stmt.Var -> {
                val value = stmt.initializer?.let { evaluate(it) } ?: UnitValue
                environment.define(stmt.name, value)
            }
            is Stmt.Block -> executeBlock(stmt.statements, Environment(environment))
            is Stmt.If -> {
                val condition = evaluate(stmt.condition)
                if (isTruthy(condition)) {
                    execute(stmt.thenBranch)
                } else if (stmt.elseBranch != null) {
                    execute(stmt.elseBranch)
                }
            }
            is Stmt.Function -> {
                val fn = UserFunctionValue(stmt, stmt.params, stmt.body, environment)
                environment.define(stmt.name, fn)
            }
            is Stmt.Return -> {
                val value = stmt.value?.let { evaluate(it) } ?: UnitValue
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

    fun evaluate(expr: Expr): Value {
        return when (expr) {
            is Expr.MatrixLiteral -> MatrixValue(Matrix(expr.rows))
            is Expr.NumberLiteral -> NumberValue(expr.value)
            is Expr.StringLiteral -> StringValue(expr.value)
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
                val scoped = Environment(environment)
                val previous = environment
                try {
                    scoped.define(expr.name, bound)
                    environment = scoped
                    evaluate(expr.bodyExpr)
                } finally {
                    environment = previous
                }
            }
            is Expr.Binary -> evalBinary(expr)
            is Expr.Unary -> {
                val right = evaluate(expr.right)
                when (expr.operator.type) {
                    TokenType.MINUS -> NumberValue(-expectNumber(expr.operator.lexeme, right))
                    else -> throw RuntimeError(expr.operator, "Unknown operator")
                }
            }
            is Expr.CallExpr -> evaluateCall(expr)
            is Expr.MethodCallExpr -> evaluateMethodCall(expr)
            is Expr.PropertyAccess -> evaluatePropertyAccess(expr)
            is Expr.Function -> {
                val body = when (val stmtBody = expr.body) {
                    is Stmt.Block -> stmtBody
                    else -> Stmt.Block(listOf(stmtBody))
                }
                UserFunctionValue(expr, expr.params, body, environment)
            }
            is Expr.LambdaExpr -> FunctionValue(expr.params, expr.body, environment)
        }
    }

    private fun evalBinary(expr: Expr.Binary): Value {
        val left = evaluate(expr.left)
        val right = evaluate(expr.right)

        return when (expr.operator.type) {
            TokenType.PLUS -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix + right.matrix)
                left is NumberValue && right is NumberValue -> NumberValue(left.value + right.value)
                left is NumberValue && right is MatrixValue -> MatrixValue(right.matrix * left.value)
                left is MatrixValue && right is NumberValue -> MatrixValue(left.matrix * right.value)
                else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
            }
            TokenType.MINUS -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix - right.matrix)
                left is NumberValue && right is NumberValue -> NumberValue(left.value - right.value)
                else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
            }
            TokenType.MULTIPLY -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix * right.matrix)
                left is NumberValue && right is MatrixValue -> MatrixValue(right.matrix * left.value)
                left is MatrixValue && right is NumberValue -> MatrixValue(left.matrix * right.value)
                left is NumberValue && right is NumberValue -> NumberValue(left.value * right.value)
                else -> throw RuntimeError(expr.operator, "Operands must be numbers or matrices")
            }
            TokenType.DIVIDE -> {
                val l = expectNumber(expr.operator.lexeme, left)
                val r = expectNumber(expr.operator.lexeme, right)
                NumberValue(l / r)
            }
            TokenType.EQ -> BoolValue(isEqual(left, right))
            TokenType.NEQ -> BoolValue(!isEqual(left, right))
            TokenType.LT -> BoolValue(expectNumber(expr.operator.lexeme, left) < expectNumber(expr.operator.lexeme, right))
            TokenType.GT -> BoolValue(expectNumber(expr.operator.lexeme, left) > expectNumber(expr.operator.lexeme, right))
            TokenType.LTE -> BoolValue(expectNumber(expr.operator.lexeme, left) <= expectNumber(expr.operator.lexeme, right))
            TokenType.GTE -> BoolValue(expectNumber(expr.operator.lexeme, left) >= expectNumber(expr.operator.lexeme, right))
            else -> throw RuntimeError(expr.operator, "Unknown operator")
        }
    }

    private fun evaluateCall(expr: Expr.CallExpr): Value {
        val callee = evaluate(expr.callee)
        val arguments = expr.args.map { evaluate(it) }

        val callable = expectCallable("call", callee)
        if (arguments.size != callable.arity()) {
            val dummyToken = Token(TokenType.IDENTIFIER, "function_call", null, 0, 0)
            throw RuntimeError(dummyToken, "Expected ${callable.arity()} arguments but got ${arguments.size}")
        }
        return callable.call(this, arguments)
    }

    private fun evaluateMethodCall(expr: Expr.MethodCallExpr): Value {
        val receiver = evaluate(expr.receiver)
        val args = expr.args.map { evaluate(it) }

        return when (expr.method) {
            "map" -> {
                val fn = expectCallable("map", args.firstOrNull()
                    ?: throw RuntimeError(Token(TokenType.IDENTIFIER, expr.method, null, 0, 0),
                        "Method '${expr.method}' expects a lambda"))
                applyMap(receiver, fn)
            }
            "reduce" -> {
                val initial = args.getOrNull(0) ?: UnitValue
                val fn = expectCallable("reduce", args.getOrNull(1)
                    ?: throw RuntimeError(Token(TokenType.IDENTIFIER, expr.method, null, 0, 0),
                        "Method '${expr.method}' expects initial value and lambda"))
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

    private fun evaluatePropertyAccess(expr: Expr.PropertyAccess): Value {
        val obj = evaluate(expr.receiver)
        return when (obj) {
            is MatrixValue -> when (expr.name) {
                "rows" -> NumberValue(obj.matrix.numRows.toDouble())
                "cols" -> NumberValue(obj.matrix.numCols.toDouble())
                "transpose" -> MatrixValue(obj.matrix.transpose())
                else -> throw RuntimeError(
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                    "Undefined property '${expr.name}' on matrix"
                )
            }
            is PairValue -> when (expr.name) {
                "first" -> obj.first
                "second" -> obj.second
                else -> throw RuntimeError(
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                    "Undefined property '${expr.name}' on pair"
                )
            }
            is ListValue -> when (expr.name) {
                "size" -> NumberValue(obj.items.size.toDouble())
                else -> throw RuntimeError(
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0),
                    "Undefined property '${expr.name}' on list"
                )
            }
            else -> {
                val dummyToken = Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                throw RuntimeError(dummyToken, "Undefined property '${expr.name}'")
            }
        }
    }

    private fun expectCallable(name: String, value: Value): CallableValue {
        return value as? CallableValue ?: throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0, 0),
            "Expected lambda/function as argument for '$name'"
        )
    }

    private fun expectNumber(name: String, value: Value): Double {
        return (value as? NumberValue)?.value ?: throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0, 0),
            "Expected number value"
        )
    }

    private fun expectMatrix(name: String, value: Value): Matrix {
        return (value as? MatrixValue)?.matrix ?: throw RuntimeError(
            Token(TokenType.IDENTIFIER, name, null, 0, 0),
            "Expected matrix value"
        )
    }

    private fun applyMap(target: Value, function: CallableValue): Value {
        if (function.arity() != 1) {
            throw RuntimeError(Token(TokenType.IDENTIFIER, "map", null, 0, 0),
                "Expected function with 1 parameter for 'map'")
        }
        return when (target) {
            is MatrixValue -> {
                val mapped = target.matrix.rows.map { row ->
                    row.map { value ->
                        val result = function.call(this, listOf(NumberValue(value)))
                        (result as? NumberValue)?.value ?: throw RuntimeError(
                            Token(TokenType.IDENTIFIER, "map", null, 0, 0),
                            "Matrix map expects lambda to return number"
                        )
                    }
                }
                MatrixValue(Matrix(mapped))
            }
            is ListValue -> ListValue(target.items.map { function.call(this, listOf(it)) })
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "map", null, 0, 0),
                "Can only map over matrices or lists"
            )
        }
    }

    private fun applyReduce(target: Value, initial: Value, function: CallableValue): Value {
        if (function.arity() != 2) {
            throw RuntimeError(Token(TokenType.IDENTIFIER, "reduce", null, 0, 0),
                "Expected function with 2 parameters for 'reduce'")
        }
        val items = flattenCollection(target, "reduce")
        var accumulator = initial
        for (value in items) {
            accumulator = function.call(this, listOf(accumulator, value))
        }
        return accumulator
    }

    private fun applyFilter(target: Value, predicate: CallableValue): Value {
        if (predicate.arity() != 1) {
            throw RuntimeError(Token(TokenType.IDENTIFIER, "filter", null, 0, 0),
                "Expected function with 1 parameter for 'filter'")
        }
        return when (target) {
            is MatrixValue -> {
                val filteredRows = target.matrix.rows.map { row ->
                    row.filter { value ->
                        isTruthy(predicate.call(this, listOf(NumberValue(value))))
                    }
                }.filter { it.isNotEmpty() }
                if (filteredRows.isEmpty()) return MatrixValue(Matrix(emptyList()))
                val width = filteredRows.first().size
                if (!filteredRows.all { it.size == width }) {
                    throw RuntimeError(
                        Token(TokenType.IDENTIFIER, "filter", null, 0, 0),
                        "Filter over matrix must keep consistent row sizes"
                    )
                }
                MatrixValue(Matrix(filteredRows))
            }
            is ListValue -> ListValue(target.items.filter { isTruthy(predicate.call(this, listOf(it))) })
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "filter", null, 0, 0),
                "Can only filter matrices or lists"
            )
        }
    }

    private fun applyZip(left: Value, right: Value): Value {
        val leftItems = flattenCollection(left, "zip")
        val rightItems = flattenCollection(right, "zip")
        val size = minOf(leftItems.size, rightItems.size)
        val pairs = (0 until size).map { i -> PairValue(leftItems[i], rightItems[i]) }
        return ListValue(pairs)
    }

    private fun applyUnzip(value: Value): Value {
        val pairs = when (value) {
            is ListValue -> value.items
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, "unzip", null, 0, 0),
                "unzip expects a list of pairs"
            )
        }

        val first = mutableListOf<Value>()
        val second = mutableListOf<Value>()
        for (pair in pairs) {
            if (pair !is PairValue) {
                throw RuntimeError(
                    Token(TokenType.IDENTIFIER, "unzip", null, 0, 0),
                    "unzip expects a list of pairs"
                )
            }
            first.add(pair.first)
            second.add(pair.second)
        }
        return PairValue(ListValue(first), ListValue(second))
    }

    private fun flattenCollection(target: Value, opName: String): List<Value> {
        return when (target) {
            is MatrixValue -> target.matrix.rows.flatten().map { NumberValue(it) }
            is ListValue -> target.items
            else -> throw RuntimeError(
                Token(TokenType.IDENTIFIER, opName, null, 0, 0),
                "$opName expects a matrix or a list"
            )
        }
    }

    private fun isTruthy(value: Value): Boolean {
        return when (value) {
            is BoolValue -> value.value
            UnitValue -> false
            else -> true
        }
    }

    private fun isEqual(a: Value, b: Value): Boolean {
        return when {
            a is NumberValue && b is NumberValue -> a.value == b.value
            a is MatrixValue && b is MatrixValue -> a.matrix == b.matrix
            a is StringValue && b is StringValue -> a.value == b.value
            a is BoolValue && b is BoolValue -> a.value == b.value
            a is ListValue && b is ListValue -> a.items == b.items
            a is PairValue && b is PairValue -> isEqual(a.first, b.first) && isEqual(a.second, b.second)
            else -> false
        }
    }

    private fun stringify(value: Value): String {
        return when (value) {
            UnitValue -> "unit"
            is NumberValue -> {
                val text = value.value.toString()
                if (text.endsWith(".0")) text.dropLast(2) else "%.4f".format(value.value)
            }
            is StringValue -> value.value
            is BoolValue -> value.value.toString()
            is PairValue -> "(${stringify(value.first)}, ${stringify(value.second)})"
            is ListValue -> value.items.joinToString(prefix = "[", postfix = "]") { stringify(it) }
            is MatrixValue -> {
                val matrix = value.matrix
                if (matrix.numRows <= 5 && matrix.numCols <= 5) {
                    matrix.rows.joinToString("\n") { row ->
                        "[${row.joinToString(", ") { "%.2f".format(it) }}]"
                    }
                } else {
                    "Matrix(${matrix.numRows}x${matrix.numCols})"
                }
            }
            is CallableValue -> value.toString()
        }
    }
}

class Environment(private val enclosing: Environment? = null) {
    private val values = mutableMapOf<String, Value>()

    fun define(name: String, value: Value) {
        values[name] = value
    }

    fun get(name: String): Value {
        return when {
            values.containsKey(name) -> values[name]!!
            enclosing != null -> enclosing.get(name)
            else -> throw RuntimeError(Token(TokenType.IDENTIFIER, name, null, 0, 0), "Undefined variable '$name'")
        }
    }

    fun assign(name: String, value: Value) {
        when {
            values.containsKey(name) -> values[name] = value
            enclosing != null -> enclosing.assign(name, value)
            else -> throw RuntimeError(Token(TokenType.IDENTIFIER, name, null, 0, 0), "Undefined variable '$name'")
        }
    }
}
