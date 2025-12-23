package core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.core.Matrix
import org.example.extensions.hadamardProduct
import org.example.extensions.mean
import org.example.extensions.normalize
import org.example.extensions.pow
import org.example.extensions.sum

class MatrixExtensionsTest {

    @Test
    fun `hadamard product multiplies element-wise`() {
        val a = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
        val b = Matrix(listOf(listOf(2.0, -1.0), listOf(0.5, 0.25)))

        val product = a.hadamardProduct(b)
        val expected = Matrix(listOf(listOf(2.0, -2.0), listOf(1.5, 1.0)))

        assertEquals(expected, product)
    }

    @Test
    fun `normalize scales matrix to 0-1`() {
        val matrix = Matrix(
            listOf(
                listOf(-2.0, 2.0, 4.0),
                listOf(0.0, 6.0, 6.0)
            )
        )

        val normalized = matrix.normalize()

        val values = normalized.toList().flatten()
        assertTrue(values.all { it in 0.0..1.0 })
        assertEquals(0.0, normalized[0, 0], absoluteTolerance)
        assertEquals(1.0, normalized[1, 1], absoluteTolerance)
    }

    @Test
    fun `pow sum and mean work together`() {
        val matrix = Matrix(listOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))

        val squared = matrix.pow(2)
        val expected = Matrix(listOf(listOf(1.0, 4.0), listOf(9.0, 16.0)))
        assertEquals(expected, squared)

        assertEquals(30.0, squared.sum(), absoluteTolerance)
        assertEquals(7.5, squared.mean(), absoluteTolerance)
    }

    private companion object {
        const val absoluteTolerance = 1e-9
    }
}
