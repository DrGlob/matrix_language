package org.example.parser

class ReturnException(val value: Value) : RuntimeException("Return statement")

/**
 * Интерпретатор statement-level конструкций.
 *
 * Хранит глобальное окружение, устанавливает StdLib и делегирует
 * вычисление выражений в [Evaluator].
 */
class Interpreter {
    internal var globals = Environment()
    internal var environment: Environment = globals
    internal val evaluator = Evaluator(this)

    init {
        globals = StdLib.install(Environment(), evaluator)
        environment = globals
    }

    fun interpret(statements: List<Stmt>) {
        try {
            for (statement in statements) {
                execute(statement)
            }
        } catch (error: ReturnException) {
            throw runtimeError("Return statement outside function", cause = error)
        } catch (error: MatrixLangRuntimeException) {
            throw error
        } catch (error: Exception) {
            throw runtimeError(error.message ?: "Unexpected error", cause = error)
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
                environment = environment.define(stmt.name, value)
            }
            is Stmt.Block -> executeBlock(stmt.statements, Environment(parent = environment))
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
                environment = environment.define(stmt.name, fn)
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
