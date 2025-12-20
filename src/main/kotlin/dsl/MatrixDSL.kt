package org.example.dsl

import org.example.core.MatMulConfig
import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MultiplicationAlgorithm

/**
 * Kotlin-DSL фасад для сборки матричных вычислений поверх [Matrix].
 *
 * Предоставляет простые операции над контекстом (declare/get),
 * функциональные конструкции (ifThen/loop/foldRange/mapRange)
 * и вычисление матричных полиномов через [poly].
 */
class MatrixDSL {
    private val context = MatrixContext()
    private val controlFlow = ControlFlow(context)

    // DSL методы

    fun declare(name: String, value: Matrix) = context.declare(name, value)

    fun get(name: String): Matrix = context[name]

    fun ifThen(condition: () -> Boolean, thenBlock: () -> Matrix, elseBlock: () -> Matrix): Matrix {
        return controlFlow.ifThen(condition, thenBlock, elseBlock)
    }

    fun <T> loop(
        condition: (T) -> Boolean,
        update: (T) -> T,
        initial: T,
        body: (T) -> T
    ): T {
        return controlFlow.loop(condition, update, initial, body)
    }

    // Добавляем публичные методы для control flow
    fun <T> foldRange(
        start: Int,
        end: Int,
        initial: T,
        op: (T, Int) -> T
    ): T {
        return controlFlow.foldRange(start, end, initial, op)
    }

    fun mapRange(
        start: Int,
        end: Int,
        transform: (Int) -> Double
    ): List<Double> {
        return controlFlow.mapRange(start, end, transform)
    }

    fun poly(
        base: Matrix,
        coefficients: List<Double>,
        config: MatMulConfig = MatMulDefaults.default().copy(algorithm = MultiplicationAlgorithm.PARALLEL),
        logMetrics: Boolean = false
    ): Matrix = base.polyEval(
        coefficients,
        config = config,
        logMetrics = logMetrics
    )

    fun compute(block: MatrixDSL.() -> Matrix): Matrix {
        return block()
    }
}

// Top-level DSL builder
/**
 * Входная точка DSL: выполняет блок в контексте [MatrixDSL].
 */
fun matrixComputation(block: MatrixDSL.() -> Matrix): Matrix {
    val dsl = MatrixDSL()
    return dsl.compute(block)
}
