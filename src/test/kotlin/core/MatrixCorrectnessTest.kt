package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.core.MatMulConfig
import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MultiplicationAlgorithm
import org.example.core.multiply

class MatrixCorrectnessTest {

    @Test
    fun `addition works for small squares`() {
        val a = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
        val b = Matrix(listOf(listOf(5.0, -1.0), listOf(0.5, 0.5)))

        val sum = a + b

        assertEquals(Matrix(listOf(listOf(6.0, 1.0), listOf(3.5, 4.5))), sum)
    }

    @Test
    fun `multiplication works for 3x3`() {
        val a = Matrix(
            listOf(
                listOf(1.0, 2.0, 3.0),
                listOf(0.0, 1.0, 4.0),
                listOf(5.0, 6.0, 0.0)
            )
        )
        val b = Matrix(
            listOf(
                listOf(-2.0, 1.0, 0.0),
                listOf(3.0, 0.0, 1.0),
                listOf(4.0, -1.0, 2.0)
            )
        )

        val product = a * b

        val expected = Matrix(
            listOf(
                listOf(16.0, -2.0, 8.0),
                listOf(19.0, -4.0, 9.0),
                listOf(8.0, 5.0, 6.0)
            )
        )
        assertEquals(expected, product)
    }

    @Test
    fun `rectangular multiplication produces expected result`() {
        val a = Matrix(
            listOf(
                listOf(1.0, 2.0, 3.0),
                listOf(4.0, 5.0, 6.0)
            )
        ) // 2x3
        val b = Matrix(
            listOf(
                listOf(7.0, 8.0),
                listOf(9.0, 10.0),
                listOf(11.0, 12.0)
            )
        ) // 3x2

        val product = a * b
        val expected = Matrix(
            listOf(
                listOf(58.0, 64.0),
                listOf(139.0, 154.0)
            )
        )

        assertEquals(expected, product)
    }

    @Test
    fun `all algorithms agree on small square`() {
        val a = Matrix.fromInitializer(3, 3) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(3, 3) { r, c -> (r * 2 - c + 2).toDouble() }

        val sequential = multiply(
            a,
            b,
            MatMulDefaults.default().copy(algorithm = MultiplicationAlgorithm.SEQUENTIAL, blockSize = 2)
        )
        val parallel = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.PARALLEL,
                blockSize = 2,
                parallelism = 2
            )
        )
        val strassen = multiply(
            a,
            b,
            MatMulConfig(
                algorithm = MultiplicationAlgorithm.STRASSEN,
                blockSize = MatMulDefaults.DEFAULT_BLOCK_SIZE,
                strassenThreshold = 1,
                parallelism = 1
            )
        )

        assertEquals(sequential, parallel)
        assertEquals(sequential, strassen)
    }

    @Test
    fun `sequential and parallel agree on rectangular`() {
        val a = Matrix.fromInitializer(2, 4) { r, c -> (r * 3 + c + 1).toDouble() }
        val b = Matrix.fromInitializer(4, 3) { r, c -> (r + c + 2).toDouble() }

        val seq = multiply(
            a,
            b,
            MatMulDefaults.default().copy(algorithm = MultiplicationAlgorithm.SEQUENTIAL, blockSize = 2)
        )
        val par = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.PARALLEL,
                blockSize = 2,
                parallelism = 2
            )
        )

        assertEquals(seq, par)
        assertTrue(seq.numRows == a.numRows && seq.numCols == b.numCols)
    }
}
