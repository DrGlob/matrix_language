package org.example.core

import kotlin.system.measureTimeMillis

/**
 * Простая модель операции с матрицами для планировщика.
 */
interface MatrixComputation {
    val rows: Int
    val cols: Int
    val costEstimate: Double
}

/**
 * Метаданные выполнения (например, для логирования планировщику).
 */
data class ExecutionMetrics(
    val rows: Int,
    val cols: Int,
    val algorithm: MultiplicationAlgorithm,
    val durationMillis: Long,
    val costEstimate: Double
)

/**
 * Операция умножения, которую можно исполнять с выбранной стратегией.
 */
class MatrixMultiplicationOp(
    private val left: Matrix,
    private val right: Matrix,
    private val config: MatMulConfig = MatMulDefaults.default()
) : MatrixComputation {
    override val rows: Int get() = left.numRows
    override val cols: Int get() = right.numCols
    override val costEstimate: Double
        get() = left.numRows * right.numCols * left.numCols.toDouble()

    fun execute(logMetrics: Boolean = false): Pair<Matrix, ExecutionMetrics?> {
        var metrics: ExecutionMetrics? = null
        val result = if (logMetrics) {
            var produced: Matrix
            val duration = measureTimeMillis {
                produced = multiply(left, right, config)
            }
            metrics = ExecutionMetrics(
                rows = rows,
                cols = cols,
                algorithm = config.algorithm,
                durationMillis = duration,
                costEstimate = costEstimate
            )
            produced
        } else {
            multiply(left, right, config)
        }
        return result to metrics
    }
}
