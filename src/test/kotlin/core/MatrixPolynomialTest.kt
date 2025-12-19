package core

import org.example.core.Matrix
import org.example.core.MatrixFactory
import org.example.core.MatMulDefaults
import org.example.core.MultiplicationAlgorithm
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MatrixPolynomialTest {

    @Test
    fun `polyEval matches manual polynomial`() {
        val a = MatrixFactory.create(
            listOf(1.0, 2.0),
            listOf(3.0, 4.0)
        )
        val coefficients = listOf(2.0, 3.0, 1.0) // 2*A^2 + 3*A + I

        val config = MatMulDefaults.default().copy(
            algorithm = MultiplicationAlgorithm.PARALLEL,
            blockSize = 2,
            parallelism = 2
        )
        val result = a.polyEval(coefficients, config = config)

        val expected = MatrixFactory.create(
            listOf(18.0, 26.0),
            listOf(39.0, 57.0)
        )
        assertEquals(expected, result)
    }

    @Test
    fun `polyEval requires square matrix`() {
        val rectangular = Matrix.fromInitializer(2, 3) { r, c -> (r + c + 1).toDouble() }
        assertFailsWith<IllegalArgumentException> {
            rectangular.polyEval(listOf(1.0, 2.0))
        }
    }
}
