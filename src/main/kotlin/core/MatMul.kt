package org.example.core

import kotlin.math.max
import kotlinx.coroutines.runBlocking

data class MatMulConfig(
    val algorithm: MultiplicationAlgorithm,
    val blockSize: Int,
    val strassenThreshold: Int,
    val parallelism: Int
)

object MatMulDefaults {
    const val DEFAULT_BLOCK_SIZE = 128
    const val DEFAULT_STRASSEN_THRESHOLD = 64
    const val DEFAULT_PARALLELISM = 1

    fun default(): MatMulConfig = MatMulConfig(
        algorithm = MultiplicationAlgorithm.SEQUENTIAL,
        blockSize = DEFAULT_BLOCK_SIZE,
        strassenThreshold = DEFAULT_STRASSEN_THRESHOLD,
        parallelism = max(DEFAULT_PARALLELISM, Runtime.getRuntime().availableProcessors())
    )
}

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
