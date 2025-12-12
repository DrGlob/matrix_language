package org.example.parser

import org.example.core.MatrixFactory
import kotlin.math.sqrt

// Исключения
class RuntimeError(val token: Token, message: String) : RuntimeException(message)
class ReturnException(val value: Value) : RuntimeException("Return statement")

class Interpreter {
    internal val globals = Environment()
    internal var environment: Environment = globals
    internal val evaluator = Evaluator(this)

    init {
        defineNative("map", 2) { ev, args ->
            val fn = ev.expectCallable("map", args[1])
            ev.applyMap(args[0], fn)
        }
        defineNative("reduce", 3) { ev, args ->
            val fn = ev.expectCallable("reduce", args[2])
            ev.applyReduce(args[0], args[1], fn)
        }
        defineNative("zip", 2) { ev, args -> ev.applyZip(args[0], args[1]) }
        defineNative("unzip", 1) { ev, args -> ev.applyUnzip(args[0]) }
        defineNative("filter", 2) { ev, args ->
            val predicate = ev.expectCallable("filter", args[1])
            ev.applyFilter(args[0], predicate)
        }
        defineNative("compose", 2) { ev, args ->
            val f = ev.expectCallable("compose", args[0])
            val g = ev.expectCallable("compose", args[1])
            NativeFunctionValue(g.arity()) { innerEv, callArgs ->
                val intermediate = g.call(innerEv, callArgs)
                f.call(innerEv, listOf(intermediate))
            }
        }
        defineNative("zeros", 2) { ev, args ->
            val rows = ev.expectNumber("zeros", args[0]).toInt()
            val cols = ev.expectNumber("zeros", args[1]).toInt()
            MatrixValue(MatrixFactory.zeros(rows, cols))
        }
        defineNative("ones", 2) { ev, args ->
            val rows = ev.expectNumber("ones", args[0]).toInt()
            val cols = ev.expectNumber("ones", args[1]).toInt()
            MatrixValue(MatrixFactory.ones(rows, cols))
        }
        defineNative("identity", 1) { ev, args ->
            val size = ev.expectNumber("identity", args[0]).toInt()
            MatrixValue(MatrixFactory.identity(size))
        }
        defineNative("transpose", 1) { ev, args ->
            MatrixValue(ev.expectMatrix("transpose", args[0]).transpose())
        }
        defineNative("rows", 1) { ev, args ->
            NumberValue(ev.expectMatrix("rows", args[0]).numRows.toDouble())
        }
        defineNative("cols", 1) { ev, args ->
            NumberValue(ev.expectMatrix("cols", args[0]).numCols.toDouble())
        }
        defineNative("norm", 1) { ev, args ->
            val matrix = ev.expectMatrix("norm", args[0])
            var sum = 0.0
            for (r in 0 until matrix.numRows) {
                for (c in 0 until matrix.numCols) {
                    val v = matrix[r, c]
                    sum += v * v
                }
            }
            NumberValue(sqrt(sum))
        }
    }

    private fun defineNative(name: String, arity: Int, impl: (Evaluator, List<Value>) -> Value) {
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
            is Stmt.Expression -> evaluator.eval(stmt.expression, environment)
            is Stmt.Print -> {
                val value = evaluator.eval(stmt.expression, environment)
                println(evaluator.stringify(value))
            }
            is Stmt.Var -> {
                val value = stmt.initializer?.let { evaluator.eval(it, environment) } ?: UnitValue
                environment.define(stmt.name, value)
            }
            is Stmt.Block -> executeBlock(stmt.statements, Environment(environment))
            is Stmt.If -> {
                val condition = evaluator.eval(stmt.condition, environment)
                if (evaluator.isTruthy(condition)) {
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
                val value = stmt.value?.let { evaluator.eval(it, environment) } ?: UnitValue
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
