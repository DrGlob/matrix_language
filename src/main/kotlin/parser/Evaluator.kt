package org.example.parser

import org.example.core.Matrix
import org.example.core.Vector

/**
 * Вычислитель выражений AST в значения [Value].
 */
class Evaluator(val interpreter: Interpreter) {
    fun eval(expr: Expr, env: Environment): Value {
        return when (expr) {
            is Expr.MatrixLiteral -> MatrixValue(Matrix(expr.rows))
            is Expr.NumberLiteral -> NumberValue(expr.value)
            is Expr.StringLiteral -> StringValue(expr.value)
            is Expr.Variable -> env.get(expr.name)
            is Expr.Assign -> {
                eval(expr.value, env)
                throw runtimeError(
                    "Reassignment is not supported with immutable environment",
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                )
            }
            is Expr.IfExpr -> {
                val condition = eval(expr.condition, env)
                if (isTruthy(condition)) eval(expr.thenBranch, env) else eval(expr.elseBranch, env)
            }
            is Expr.LetInExpr -> {
                val bound = eval(expr.boundExpr, env)
                val scoped = Environment(parent = env).define(expr.name, bound)
                eval(expr.bodyExpr, scoped)
            }
            is Expr.Binary -> evalBinary(expr, env)
            is Expr.Unary -> {
                val right = eval(expr.right, env)
                when (expr.operator.type) {
                    TokenType.MINUS -> NumberValue(-expectNumber(expr.operator.lexeme, right))
                    else -> throw runtimeError("Unknown operator", expr.operator)
                }
            }
            is Expr.CallExpr -> evalCall(expr, env)
            is Expr.MethodCallExpr -> evalMethodCall(expr, env)
            is Expr.PropertyAccess -> evalProperty(expr, env)
            is Expr.Function -> {
                val body = when (val stmtBody = expr.body) {
                    is Stmt.Block -> stmtBody
                    else -> Stmt.Block(listOf(stmtBody))
                }
                UserFunctionValue(expr, expr.params, body, env)
            }
            is Expr.LambdaExpr -> FunctionValue(expr.params, expr.body, env)
        }
    }

    private fun evalBinary(expr: Expr.Binary, env: Environment): Value {
        val left = eval(expr.left, env)
        val right = eval(expr.right, env)

        return when (expr.operator.type) {
            TokenType.PLUS -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix + right.matrix)
                left is NumberValue && right is NumberValue -> NumberValue(left.value + right.value)
                left is NumberValue && right is MatrixValue -> MatrixValue(right.matrix * left.value)
                left is MatrixValue && right is NumberValue -> MatrixValue(left.matrix * right.value)
                else -> throw runtimeError("Operands must be numbers or matrices", expr.operator)
            }
            TokenType.MINUS -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix - right.matrix)
                left is NumberValue && right is NumberValue -> NumberValue(left.value - right.value)
                else -> throw runtimeError("Operands must be numbers or matrices", expr.operator)
            }
            TokenType.MULTIPLY -> when {
                left is MatrixValue && right is MatrixValue -> MatrixValue(left.matrix * right.matrix)
                left is NumberValue && right is MatrixValue -> MatrixValue(right.matrix * left.value)
                left is MatrixValue && right is NumberValue -> MatrixValue(left.matrix * right.value)
                left is NumberValue && right is NumberValue -> NumberValue(left.value * right.value)
                else -> throw runtimeError("Operands must be numbers or matrices", expr.operator)
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
            else -> throw runtimeError("Unknown operator", expr.operator)
        }
    }

    private fun evalCall(expr: Expr.CallExpr, env: Environment): Value {
        val callee = eval(expr.callee, env)
        val arguments = expr.args.map { eval(it, env) }

        val callable = expectCallable("call", callee)
        if (arguments.size != callable.arity()) {
            val dummyToken = Token(TokenType.IDENTIFIER, "function_call", null, 0, 0)
            throw runtimeError("Expected ${callable.arity()} arguments but got ${arguments.size}", dummyToken)
        }
        return callable.call(this, arguments)
    }

    private fun evalMethodCall(expr: Expr.MethodCallExpr, env: Environment): Value {
        val receiver = eval(expr.receiver, env)
        val args = expr.args.map { eval(it, env) }

        return when (expr.method) {
            "map" -> {
                val fn = expectCallable("map", args.firstOrNull()
                    ?: throw runtimeError(
                        "Method '${expr.method}' expects a lambda",
                        Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                    ))
                applyMap(receiver, fn)
            }
            "reduce" -> {
                val initial = args.getOrNull(0)
                    ?: throw runtimeError(
                        "Method '${expr.method}' expects initial value and lambda",
                        Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                    )
                val fn = expectCallable("reduce", args.getOrNull(1)
                    ?: throw runtimeError(
                        "Method '${expr.method}' expects initial value and lambda",
                        Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                    ))
                applyReduce(receiver, initial, fn)
            }
            "zip" -> {
                val other = args.getOrNull(0)
                    ?: throw runtimeError(
                        "Method '${expr.method}' expects another collection",
                        Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                    )
                applyZip(receiver, other)
            }
            "unzip" -> applyUnzip(receiver)
            else -> {
                val dummyToken = Token(TokenType.IDENTIFIER, expr.method, null, 0, 0)
                throw runtimeError("Unknown method '${expr.method}'", dummyToken)
            }
        }
    }

    private fun evalProperty(expr: Expr.PropertyAccess, env: Environment): Value {
        val obj = eval(expr.receiver, env)
        return when (obj) {
            is MatrixValue -> when (expr.name) {
                "rows" -> NumberValue(obj.matrix.numRows.toDouble())
                "cols" -> NumberValue(obj.matrix.numCols.toDouble())
                "transpose" -> MatrixValue(obj.matrix.transpose())
                else -> throw runtimeError(
                    "Undefined property '${expr.name}' on matrix",
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                )
            }
            is PairValue -> when (expr.name) {
                "first" -> obj.first
                "second" -> obj.second
                else -> throw runtimeError(
                    "Undefined property '${expr.name}' on pair",
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                )
            }
            is ListValue -> when (expr.name) {
                "size" -> NumberValue(obj.items.size.toDouble())
                else -> throw runtimeError(
                    "Undefined property '${expr.name}' on list",
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                )
            }
            is VectorValue -> when (expr.name) {
                "size" -> NumberValue(obj.vector.size.toDouble())
                else -> throw runtimeError(
                    "Undefined property '${expr.name}' on vector",
                    Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                )
            }
            else -> {
                val dummyToken = Token(TokenType.IDENTIFIER, expr.name, null, 0, 0)
                throw runtimeError("Undefined property '${expr.name}'", dummyToken)
            }
        }
    }

    fun expectCallable(name: String, value: Value): CallableValue {
        return value as? CallableValue ?: throw runtimeError(
            "Expected lambda/function as argument for '$name'",
            Token(TokenType.IDENTIFIER, name, null, 0, 0)
        )
    }

    fun expectNumber(name: String, value: Value): Double {
        return (value as? NumberValue)?.value ?: throw runtimeError(
            "Expected number value",
            Token(TokenType.IDENTIFIER, name, null, 0, 0)
        )
    }

    fun expectMatrix(name: String, value: Value): Matrix {
        return (value as? MatrixValue)?.matrix ?: throw runtimeError(
            "Expected matrix value",
            Token(TokenType.IDENTIFIER, name, null, 0, 0)
        )
    }

    fun expectVector(name: String, value: Value): Vector {
        return when (value) {
            is VectorValue -> value.vector
            is MatrixValue -> Vector.fromMatrix(value.matrix)
            is ListValue -> {
                val doubles = value.items.mapIndexed { idx, v -> expectNumber("$name[$idx]", v) }
                Vector.fromList(doubles)
            }
            else -> throw runtimeError(
                "Expected vector value",
                Token(TokenType.IDENTIFIER, name, null, 0, 0)
            )
        }
    }

    fun applyMap(target: Value, function: CallableValue): Value {
        if (function.arity() != 1) {
            throw runtimeError(
                "Expected function with 1 parameter for 'map'",
                Token(TokenType.IDENTIFIER, "map", null, 0, 0)
            )
        }
        return when (target) {
            is MatrixValue -> {
                val m = target.matrix
                MatrixValue(
                    Matrix.fromInitializer(m.numRows, m.numCols) { r, c ->
                        val value = m[r, c]
                        val result = function.call(this, listOf(NumberValue(value)))
                        (result as? NumberValue)?.value ?: throw runtimeError(
                            "Matrix map expects lambda to return number",
                            Token(TokenType.IDENTIFIER, "map", null, 0, 0)
                        )
                    }
                )
            }
            is VectorValue -> {
                val mapped = target.vector.map { value ->
                    val result = function.call(this, listOf(NumberValue(value)))
                    (result as? NumberValue)?.value ?: throw runtimeError(
                        "Vector map expects lambda to return number",
                        Token(TokenType.IDENTIFIER, "map", null, 0, 0)
                    )
                }
                VectorValue(mapped)
            }
            is ListValue -> ListValue(target.items.map { function.call(this, listOf(it)) })
            else -> throw runtimeError(
                "Can only map over matrices, vectors or lists",
                Token(TokenType.IDENTIFIER, "map", null, 0, 0)
            )
        }
    }

    fun applyReduce(target: Value, initial: Value, function: CallableValue): Value {
        if (function.arity() != 2) {
            throw runtimeError(
                "Expected function with 2 parameters for 'reduce'",
                Token(TokenType.IDENTIFIER, "reduce", null, 0, 0)
            )
        }
        if (target is VectorValue) {
            var acc = expectNumber("reduce", initial)
            val vector = target.vector
            for (idx in 0 until vector.size) {
                val result = function.call(
                    this,
                    listOf(NumberValue(acc), NumberValue(vector[idx]))
                )
                acc = (result as? NumberValue)?.value ?: throw runtimeError(
                    "Vector reduce expects lambda to return number",
                    Token(TokenType.IDENTIFIER, "reduce", null, 0, 0)
                )
            }
            return NumberValue(acc)
        }
        val items = flattenCollection(target, "reduce")
        var accumulator = initial
        for (value in items) {
            accumulator = function.call(this, listOf(accumulator, value))
        }
        return accumulator
    }

    fun applyFilter(target: Value, predicate: CallableValue): Value {
        if (predicate.arity() != 1) {
            throw runtimeError(
                "Expected function with 1 parameter for 'filter'",
                Token(TokenType.IDENTIFIER, "filter", null, 0, 0)
            )
        }
        return when (target) {
            is MatrixValue -> {
                val filteredRows = (0 until target.matrix.numRows).map { r ->
                    (0 until target.matrix.numCols).mapNotNull { c ->
                        val value = target.matrix[r, c]
                        if (isTruthy(predicate.call(this, listOf(NumberValue(value))))) value else null
                    }
                }.filter { it.isNotEmpty() }
                if (filteredRows.isEmpty()) return MatrixValue(Matrix(emptyList()))
                val width = filteredRows.first().size
                if (!filteredRows.all { it.size == width }) {
                    throw runtimeError(
                        "Filter over matrix must keep consistent row sizes",
                        Token(TokenType.IDENTIFIER, "filter", null, 0, 0)
                    )
                }
                MatrixValue(Matrix(filteredRows))
            }
            is ListValue -> ListValue(target.items.filter { isTruthy(predicate.call(this, listOf(it))) })
            else -> throw runtimeError(
                "Can only filter matrices or lists",
                Token(TokenType.IDENTIFIER, "filter", null, 0, 0)
            )
        }
    }

    fun applyZip(left: Value, right: Value): Value {
        val leftItems = flattenCollection(left, "zip")
        val rightItems = flattenCollection(right, "zip")
        val size = minOf(leftItems.size, rightItems.size)
        val pairs = (0 until size).map { i -> PairValue(leftItems[i], rightItems[i]) }
        return ListValue(pairs)
    }

    fun applyUnzip(value: Value): Value {
        val pairs = when (value) {
            is ListValue -> value.items
            else -> throw runtimeError(
                "unzip expects a list of pairs",
                Token(TokenType.IDENTIFIER, "unzip", null, 0, 0)
            )
        }

        val first = mutableListOf<Value>()
        val second = mutableListOf<Value>()
        var allNumeric = true
        for (pair in pairs) {
            if (pair !is PairValue) {
                throw runtimeError(
                    "unzip expects a list of pairs",
                    Token(TokenType.IDENTIFIER, "unzip", null, 0, 0)
                )
            }
            first.add(pair.first)
            second.add(pair.second)
            allNumeric = allNumeric && pair.first is NumberValue && pair.second is NumberValue
        }
        if (allNumeric) {
            val leftVector = VectorValue(Vector.fromList(first.map { (it as NumberValue).value }))
            val rightVector = VectorValue(Vector.fromList(second.map { (it as NumberValue).value }))
            return PairValue(leftVector, rightVector)
        }
        return PairValue(ListValue(first), ListValue(second))
    }

    private fun flattenCollection(target: Value, opName: String): List<Value> {
        return when (target) {
            is MatrixValue -> {
                val list = mutableListOf<Value>()
                val m = target.matrix
                for (r in 0 until m.numRows) {
                    for (c in 0 until m.numCols) {
                        list.add(NumberValue(m[r, c]))
                    }
                }
                list
            }
            is VectorValue -> target.vector.toList().map { NumberValue(it) }
            is ListValue -> target.items
            else -> throw runtimeError(
                "$opName expects a matrix, a vector or a list",
                Token(TokenType.IDENTIFIER, opName, null, 0, 0)
            )
        }
    }

    fun isTruthy(value: Value): Boolean {
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
            a is VectorValue && b is VectorValue -> a.vector == b.vector
            else -> false
        }
    }

    fun stringify(value: Value): String {
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
                    (0 until matrix.numRows).joinToString("\n") { r ->
                        val row = (0 until matrix.numCols).joinToString(", ") { c ->
                            "%.2f".format(matrix[r, c])
                        }
                        "[$row]"
                    }
                } else {
                    "Matrix(${matrix.numRows}x${matrix.numCols})"
                }
            }
            is VectorValue -> {
                val entries = value.vector.toList()
                if (entries.size <= 8) {
                    entries.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) }
                } else {
                    "Vector(size=${entries.size})"
                }
            }
            is CallableValue -> value.toString()
        }
    }
}
