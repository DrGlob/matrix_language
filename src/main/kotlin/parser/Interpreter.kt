package org.example.parser

import org.example.type.TypeEnv
import org.example.type.checkStatements
import planner.AstExecMapper
import planner.AstExecutionConfig
import planner.AstExecutionEngine
import planner.AstExecutionListener
import planner.AstExecutionSink
import planner.Planner

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
    internal var typeEnvironment: TypeEnv = TypeEnv()
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

    fun executePipeline(
        statements: List<Stmt>,
        config: AstExecutionConfig = AstExecutionConfig(),
        listener: AstExecutionListener? = null,
        sink: AstExecutionSink? = null
    ): Value? {
        if (config.enableTypeCheck) {
            typeEnvironment = checkStatements(statements, typeEnvironment)
        }

        var lastValue: Value? = null
        try {
            for (statement in statements) {
                val value = executeWithPlan(statement, config, listener, sink)
                if (value != null) {
                    lastValue = value
                }
            }
            return lastValue
        } catch (error: ReturnException) {
            throw runtimeError("Return statement outside function", cause = error)
        } catch (error: MatrixLangRuntimeException) {
            throw error
        } catch (error: Exception) {
            throw runtimeError(error.message ?: "Unexpected error", cause = error)
        }
    }

    fun run(
        source: String,
        config: AstExecutionConfig = AstExecutionConfig(),
        listener: AstExecutionListener? = null,
        sink: AstExecutionSink? = null
    ): Value? {
        val lexer = Lexer(source)
        val parser = Parser(lexer.scanTokens())
        val statements = parser.parse()
        return executePipeline(statements, config, listener, sink)
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

    private fun executeWithPlan(
        stmt: Stmt,
        config: AstExecutionConfig,
        listener: AstExecutionListener?,
        sink: AstExecutionSink?
    ): Value? {
        return when (stmt) {
            is Stmt.Expression -> evaluateWithPlan(stmt.expression, config, listener, sink)
            is Stmt.Print -> {
                val value = evaluateWithPlan(stmt.expression, config, listener, sink)
                println(evaluator.stringify(value))
                value
            }
            is Stmt.Var -> {
                val value = stmt.initializer?.let { evaluateWithPlan(it, config, listener, sink) } ?: UnitValue
                environment = environment.define(stmt.name, value)
                value
            }
            is Stmt.Block -> {
                executeBlockWithPlan(stmt.statements, Environment(parent = environment), config, listener, sink)
                UnitValue
            }
            is Stmt.If -> {
                val condition = evaluateWithPlan(stmt.condition, config, listener, sink)
                if (evaluator.isTruthy(condition)) {
                    executeWithPlan(stmt.thenBranch, config, listener, sink)
                } else if (stmt.elseBranch != null) {
                    executeWithPlan(stmt.elseBranch, config, listener, sink)
                } else {
                    UnitValue
                }
            }
            is Stmt.Function -> {
                val fn = UserFunctionValue(stmt, stmt.params, stmt.body, environment)
                environment = environment.define(stmt.name, fn)
                UnitValue
            }
            is Stmt.Return -> {
                val value = stmt.value?.let { evaluateWithPlan(it, config, listener, sink) } ?: UnitValue
                throw ReturnException(value)
            }
        }
    }

    private fun executeBlockWithPlan(
        statements: List<Stmt>,
        newEnvironment: Environment,
        config: AstExecutionConfig,
        listener: AstExecutionListener?,
        sink: AstExecutionSink?
    ) {
        val previous = this.environment
        try {
            this.environment = newEnvironment
            for (statement in statements) {
                executeWithPlan(statement, config, listener, sink)
            }
        } finally {
            this.environment = previous
        }
    }

    private fun evaluateWithPlan(
        expr: Expr,
        config: AstExecutionConfig,
        listener: AstExecutionListener?,
        sink: AstExecutionSink?
    ): Value {
        if (!config.enablePlanning) {
            return evaluator.eval(expr, environment)
        }
        val plan = Planner().plan(expr)
        val nodes = AstExecMapper.fromPlannerGraph(plan)
        val engine = AstExecutionEngine(evaluator)
        return engine.execute(nodes, expr, environment, listener, sink)
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
