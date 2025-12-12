package org.example.parser

import org.example.core.Matrix

sealed interface Value

data class NumberValue(val value: Double) : Value
data class MatrixValue(val matrix: Matrix) : Value
data class ListValue(val items: List<Value>) : Value
data class PairValue(val first: Value, val second: Value) : Value
data class StringValue(val value: String) : Value
data class BoolValue(val value: Boolean) : Value

// Функции/лямбды
interface CallableValue : Value {
    fun arity(): Int
    fun call(interpreter: Interpreter, arguments: List<Value>): Value
}

data class NativeFunctionValue(
    private val expectedArity: Int,
    private val impl: (List<Value>) -> Value
) : CallableValue {
    override fun arity(): Int = expectedArity
    override fun call(interpreter: Interpreter, arguments: List<Value>): Value =
        impl(arguments)
    override fun toString(): String = "<native fn>"
}

data class FunctionValue(
    private val params: List<String>,
    private val body: Expr,
    private val closure: Environment
) : CallableValue {
    override fun arity(): Int = params.size

    override fun call(interpreter: Interpreter, arguments: List<Value>): Value {
        val environment = Environment(closure)
        for ((i, param) in params.withIndex()) {
            environment.define(param, arguments.getOrElse(i) { UnitValue })
        }

        val previousEnv = interpreter.environment
        try {
            interpreter.environment = environment
            return interpreter.evaluate(body)
        } finally {
            interpreter.environment = previousEnv
        }
    }

    override fun toString(): String = "<lambda(${params.joinToString(", ")})>"
}

data class UserFunctionValue(
    private val declaration: Any,
    private val params: List<String>,
    private val body: Stmt,
    private val closure: Environment
) : CallableValue {
    override fun arity(): Int = params.size

    override fun call(interpreter: Interpreter, arguments: List<Value>): Value {
        val environment = Environment(closure)
        for ((i, param) in params.withIndex()) {
            environment.define(param, arguments.getOrElse(i) { UnitValue })
        }

        val previousEnv = interpreter.environment
        try {
            interpreter.environment = environment
            interpreter.execute(body)
        } catch (returnValue: ReturnException) {
            return returnValue.value
        } finally {
            interpreter.environment = previousEnv
        }
        return UnitValue
    }

    override fun toString(): String = "<function>"
}

// Отдельный singleton для "unit"
object UnitValue : Value {
    override fun toString(): String = "unit"
}
