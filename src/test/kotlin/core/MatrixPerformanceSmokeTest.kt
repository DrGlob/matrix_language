package core

import kotlin.system.measureTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import org.example.core.MatMulConfig
import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MultiplicationAlgorithm
import org.example.core.multiply

class MatrixPerformanceSmokeTest {

    @Test
    fun `log multiply timings for sequential and parallel`() {
        val size = 128
        val a = Matrix.fromInitializer(size, size) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r * 2 - c + 3).toDouble() }

        val seqConfig = MatMulDefaults.default().copy(algorithm = MultiplicationAlgorithm.SEQUENTIAL, blockSize = 32)
        val parConfig = MatMulDefaults.default().copy(
            algorithm = MultiplicationAlgorithm.PARALLEL,
            blockSize = 32,
            parallelism = 4
        )

        lateinit var seqResult: Matrix
        val seqMs = measureTimeMillis { seqResult = multiply(a, b, seqConfig) }

        val parMs = measureTimeMillis { multiply(a, b, parConfig) }

        println("Performance smoke: sequential=${seqMs}ms, parallel=${parMs}ms for ${size}x$size")
        assertEquals(size, seqResult.numRows)
        assertEquals(size, seqResult.numCols)
    }

    @Test
    fun `log strassen timing`() {
        val size = 64
        val a = Matrix.fromInitializer(size, size) { r, c -> (r - c + 5).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r + 2 * c + 1).toDouble() }

        val config = MatMulConfig(
            algorithm = MultiplicationAlgorithm.STRASSEN,
            blockSize = MatMulDefaults.DEFAULT_BLOCK_SIZE,
            strassenThreshold = 1,
            parallelism = 4
        )
        val ms = measureTimeMillis { multiply(a, b, config) }
        println("Performance smoke: Strassen=${ms}ms for ${size}x$size")
    }
}
