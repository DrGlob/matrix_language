package core

import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MultiplicationAlgorithm
import org.example.core.toBlocks
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

class BlockMatrixPerformanceTest {

    @Test
    fun `block matrix reuse beats repeated reblocking`() {
        val size = 256
        val blockSize = 64
        val repeats = 5

        val a = Matrix.fromInitializer(size, size) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r * 2 + c + 1).toDouble() }

        val blockA = a.toBlocks(blockSize)
        val blockB = b.toBlocks(blockSize)
        val config = MatMulDefaults.default().copy(
            algorithm = MultiplicationAlgorithm.SEQUENTIAL,
            blockSize = blockSize
        )

        warmup {
            a * b
            blockA.multiply(blockB, config)
        }

        val matrixTime = bestOf(3) {
            repeat(repeats) {
                a * b // оператор использует Matrix multiply с разбиением каждый раз
            }
        }
        val blockTime = bestOf(3) {
            repeat(repeats) {
                blockA.multiply(blockB, config)
            }
        }

        println("BlockMatrix reuse (sequential): matrix=${matrixTime}ms block=${blockTime}ms")
        val matrixResult = (a * b).toBlocks(blockSize)
        val blockResult = blockA.multiply(blockB, config)
        assertTrue(matrixResult.toMatrix() == blockResult.toMatrix(), "BlockMatrix result must match matrix multiplication")
        assertTrue(blockTime < matrixTime, "Reusing preblocked matrices should be faster than reblocking each time. matrix=${matrixTime}ms block=${blockTime}ms")
    }

    @Test
    fun `parallel block grid is faster than sequential matrix`() {
        val available = Runtime.getRuntime().availableProcessors()
        assumeTrue(available > 1)

        val size = 512
        val blockSize = 128
        val repeats = 2
        val parallelism = minOf(available, 8)

        val a = Matrix.fromInitializer(size, size) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r * 2 + c + 1).toDouble() }

        val blockA = a.toBlocks(blockSize)
        val blockB = b.toBlocks(blockSize)
        val parallelConfig = MatMulDefaults.default().copy(
            algorithm = MultiplicationAlgorithm.PARALLEL,
            blockSize = blockSize,
            parallelism = parallelism
        )

        warmup {
            a * b
            blockA.multiply(blockB, parallelConfig)
        }

        val matrixTime = bestOf(3) {
            repeat(repeats) {
                a * b // оператор -> последовательное умножение
            }
        }
        val blockTime = bestOf(3) {
            repeat(repeats) {
                blockA.multiply(blockB, parallelConfig)
            }
        }

        println(
            "BlockMatrix reuse (parallel=$parallelism): matrix=${matrixTime}ms block=${blockTime}ms"
        )
        val matrixResult = a * b
        val blockResult = blockA.multiply(blockB, parallelConfig).toMatrix()
        assertTrue(matrixResult == blockResult, "Parallel BlockMatrix result must match sequential matrix multiply")
        assertTrue(blockTime < matrixTime, "Parallel BlockMatrix multiply should be faster than sequential matrix multiply. matrix=${matrixTime}ms block=${blockTime}ms")
    }

    private fun warmup(block: () -> Unit) {
        repeat(2) { block() }
    }

    private fun bestOf(runs: Int, block: () -> Unit): Long {
        var best = Long.MAX_VALUE
        repeat(runs) {
            val elapsed = measureTimeMillis(block)
            if (elapsed < best) best = elapsed
        }
        return best
    }
}
