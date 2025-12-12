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
    fun call(evaluator: Evaluator, arguments: List<Value>): Value
}

data class NativeFunctionValue(
    private val expectedArity: Int,
    private val impl: (Evaluator, List<Value>) -> Value
) : CallableValue {
    override fun arity(): Int = expectedArity
    override fun call(evaluator: Evaluator, arguments: List<Value>): Value =
        impl(evaluator, arguments)
    override fun toString(): String = "<native fn>"
}

data class FunctionValue(
    private val params: List<String>,
    private val body: Expr,
    private val closure: Environment
) : CallableValue {
    override fun arity(): Int = params.size

    override fun call(evaluator: Evaluator, arguments: List<Value>): Value {
        val environment = Environment(closure)
        for ((i, param) in params.withIndex()) {
            environment.define(param, arguments.getOrElse(i) { UnitValue })
        }
        return evaluator.eval(body, environment)
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

    override fun call(evaluator: Evaluator, arguments: List<Value>): Value {
        val environment = Environment(closure)
        for ((i, param) in params.withIndex()) {
            environment.define(param, arguments.getOrElse(i) { UnitValue })
        }

        return try {
            evaluator.interpreter.executeBlock(listOf(body), environment)
            UnitValue
        } catch (returnValue: ReturnException) {
            returnValue.value
        }
    }

    override fun toString(): String = "<function>"
}

// Отдельный singleton для "unit"
object UnitValue : Value {
    override fun toString(): String = "unit"
}
