package org.example.core

import kotlinx.coroutines.runBlocking

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
    // Подбиралось на размерах 256/512/1024: 128 даёт хороший баланс кэш/накладные расходы.
    const val DEFAULT_BLOCK_SIZE = 128
    // Подбиралось на размерах 256/512/1024: ниже порога Strassen падаем до блочного O(n^3).
    const val DEFAULT_STRASSEN_THRESHOLD = 256
    // Подбиралось на размерах 256/512/1024: держим разумный лимит воркеров для PARALLEL.
    const val DEFAULT_PARALLELISM = 8

    /** Базовая конфигурация: SEQUENTIAL с подобранными блоком/порогом и лимитом параллелизма. */
    fun default(): MatMulConfig = MatMulConfig(
        algorithm = MultiplicationAlgorithm.SEQUENTIAL,
        blockSize = DEFAULT_BLOCK_SIZE,
        strassenThreshold = DEFAULT_STRASSEN_THRESHOLD,
        parallelism = Runtime.getRuntime().availableProcessors().coerceIn(1, DEFAULT_PARALLELISM)
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
