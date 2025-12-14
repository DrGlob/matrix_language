package core

import kotlinx.coroutines.runBlocking
import org.example.core.Matrix
import org.junit.jupiter.api.Test
import kotlin.system.measureTimeMillis
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MatrixBlockMultiplicationTest {

    @Test
    fun `sequential matches naive`() {
        val a = Matrix.fromInitializer(3, 2) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(2, 3) { r, c -> (r * 2 + c + 1).toDouble() }

        val expected = naiveMultiply(a, b)
        val actual = a.multiplySequential(b, blockSize = 2)

        assertEquals(expected, actual, "Sequential block multiply should match naive result")
    }

    @Test
    fun `parallel matches sequential`() = runBlocking {
        val a = Matrix.fromInitializer(4, 4) { r, c -> (r * 4 + c + 1).toDouble() }
        val b = Matrix.fromInitializer(4, 4) { r, c -> (r + c + 2).toDouble() }

        val sequential = a.multiplySequential(b, blockSize = 2)
        val parallel = a.multiplyParallel(b, blockSize = 2, parallelism = 2)

        assertEquals(sequential, parallel, "Parallel block multiply should equal sequential")
    }

    @Test
    fun `parallel multiplication shows measurable speedup`() = runBlocking {
        val size = 1600
        val a = Matrix.fromInitializer(size, size) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r * 2 + c + 1).toDouble() }

        lateinit var sequentialResult: Matrix
        val sequentialTime = measureTimeMillis {
            sequentialResult = a.multiplySequential(b, blockSize = 32)
        }

        lateinit var parallelResult: Matrix
        val parallelTime = measureTimeMillis {
            parallelResult = a.multiplyParallel(b, blockSize = 32, parallelism = 8)
        }

        assertEquals(sequentialResult, parallelResult, "Parallel result must match sequential result")
        println("Sequential time: ${sequentialTime}ms, parallel time: ${parallelTime}ms")
        assertTrue(
            parallelTime <= sequentialTime * 1.25 + 50,
            "Parallel multiply should not be significantly slower than sequential. sequential=${sequentialTime}ms parallel=${parallelTime}ms"
        )
    }

    @Test
    fun `higher parallelism improves throughput`() = runBlocking {
        val size = 192
        val a = Matrix.fromInitializer(size, size) { r, c -> (r * 3 + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r + c + 2).toDouble() }

        val timeSingle = measureTimeMillis {
            a.multiplyParallel(b, blockSize = 32, parallelism = 1)
        }

        val timeMulti = measureTimeMillis {
            a.multiplyParallel(b, blockSize = 32, parallelism = 8)
        }

        println("Parallelism=1 -> ${timeSingle}ms, parallelism=8 -> ${timeMulti}ms")
        assertTrue(
            timeMulti <= timeSingle * 1.2,
            "Using more worker coroutines should not make multiplication much slower. p1=${timeSingle}ms p8=${timeMulti}ms"
        )
    }

    @Test
    fun `operator times uses sequential path`() {
        val a = Matrix.fromInitializer(3, 3) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(3, 3) { r, c -> (r * 3 + c + 1).toDouble() }

        val viaOperator = a * b
        val viaSequential = a.multiplySequential(b, blockSize = 4) // blockSize > dims to hit single-block path

        assertEquals(viaSequential, viaOperator, "Operator * should delegate to sequential block multiply")
    }

    @Test
    fun `handles block size larger than matrix`() {
        val a = Matrix.fromInitializer(2, 2) { r, c -> (r * 2 + c + 1).toDouble() }
        val b = Matrix.fromInitializer(2, 2) { r, c -> (r + c + 2).toDouble() }

        val expected = naiveMultiply(a, b)
        val actual = a.multiplySequential(b, blockSize = 10)

        assertEquals(expected, actual, "Large block size should still work as single-block multiply")
    }

    @Test
    fun `handles non divisible dimensions`() {
        val a = Matrix.fromInitializer(5, 4) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(4, 3) { r, c -> (r * 3 + c + 1).toDouble() }

        val expected = naiveMultiply(a, b)
        val actual = a.multiplySequential(b, blockSize = 2)

        assertEquals(expected, actual, "Edges blocks must be handled correctly")
    }

    private fun naiveMultiply(a: Matrix, b: Matrix): Matrix {
        require(a.numCols == b.numRows) { "Incompatible matrices" }
        return Matrix.fromInitializer(a.numRows, b.numCols) { r, c ->
            var acc = 0.0
            var k = 0
            while (k < a.numCols) {
                acc += a[r, k] * b[k, c]
                k++
            }
            acc
        }
    }
}
