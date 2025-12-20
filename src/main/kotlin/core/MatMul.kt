package org.example.core

import kotlinx.coroutines.runBlocking
import kotlin.math.max

/**
 * Конфигурация умножения матриц.
 *
 * @property algorithm выбранная реализация умножения.
 * @property blockSize размер блока для блочных алгоритмов (SEQUENTIAL/PARALLEL).
 * @property strassenThreshold размер, при котором STRASSEN переключается на базовый алгоритм.
 * @property parallelism лимит параллельных задач для PARALLEL.
 */
data class MatMulConfig(
    val algorithm: MultiplicationAlgorithm,
    val blockSize: Int,
    val strassenThreshold: Int,
    val parallelism: Int
)

/**
 * Дефолты для умножения матриц.
 */
object MatMulDefaults {
    const val DEFAULT_BLOCK_SIZE = 128
    const val DEFAULT_STRASSEN_THRESHOLD = 64
    const val DEFAULT_PARALLELISM = 1

    /**
     * Базовая конфигурация: SEQUENTIAL, блоки 128, порог Strassen 64,
     * параллелизм равен числу доступных процессоров (но не меньше 1).
     */
    fun default(): MatMulConfig = MatMulConfig(
        algorithm = MultiplicationAlgorithm.SEQUENTIAL,
        blockSize = DEFAULT_BLOCK_SIZE,
        strassenThreshold = DEFAULT_STRASSEN_THRESHOLD,
        parallelism = max(DEFAULT_PARALLELISM, Runtime.getRuntime().availableProcessors())
    )
}

/**
 * Единая точка входа для умножения матриц.
 *
 * Алгоритм выбирается строго по [config.algorithm], автоматических эвристик нет.
 * Иммутабельность: входные матрицы не изменяются.
 *
 * Исключения:
 * - [IllegalArgumentException] при несовместимых размерах.
 * - [IllegalArgumentException] для STRASSEN при неквадратных матрицах одинакового размера.
 */
fun multiply(
    a: Matrix,
    b: Matrix,
    config: MatMulConfig = MatMulDefaults.default()
): Matrix = when (config.algorithm) {
    MultiplicationAlgorithm.SEQUENTIAL -> a.multiplySequential(b, config.blockSize)
    MultiplicationAlgorithm.PARALLEL -> runBlocking {
        a.multiplyParallel(b, config.blockSize, config.parallelism)
    }
    MultiplicationAlgorithm.STRASSEN -> a.multiplyStrassen(b, config.strassenThreshold)
}
