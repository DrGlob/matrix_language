package core

import kotlinx.coroutines.runBlocking
import org.example.core.InMemoryBlockStorage
import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MatrixBlock
import org.example.core.MultiplicationAlgorithm
import org.example.core.OutOfCoreBlockMatrix
import org.example.core.multiply
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

class MatMulEdgeCasesTest {

    @Test
    fun `rectangular multiply matches across sequential and parallel`() {
        val a = Matrix.fromInitializer(3, 5) { r, c -> (r * 2 + c + 1).toDouble() }
        val b = Matrix.fromInitializer(5, 2) { r, c -> (r - c + 3).toDouble() }

        val blockSize = 3 // не кратно размерам, проверяем края блоков
        val sequential = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.SEQUENTIAL,
                blockSize = blockSize
            )
        )
        val parallel = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.PARALLEL,
                blockSize = blockSize,
                parallelism = 3
            )
        )

        assertMatricesClose(sequential, parallel)
    }

    @Test
    fun `strassen matches block algorithms on odd size`() {
        val size = 9 // проверяем паддинг до степени 2
        val a = Matrix.fromInitializer(size, size) { r, c -> (r + c + 1).toDouble() }
        val b = Matrix.fromInitializer(size, size) { r, c -> (r * 2 - c + 2).toDouble() }

        val blockSize = 4
        val sequential = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.SEQUENTIAL,
                blockSize = blockSize
            )
        )
        val parallel = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.PARALLEL,
                blockSize = blockSize,
                parallelism = 4
            )
        )
        val strassen = multiply(
            a,
            b,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.STRASSEN,
                strassenThreshold = 8
            )
        )

        assertMatricesClose(sequential, parallel)
        assertMatricesClose(sequential, strassen, epsilon = 1e-6)
    }

    @Test
    fun `tiny shapes are handled without errors`() {
        val oneByOneA = Matrix.fromInitializer(1, 1) { _, _ -> 7.0 }
        val oneByOneB = Matrix.fromInitializer(1, 1) { _, _ -> 3.0 }

        val oneByOneSequential = multiply(
            oneByOneA,
            oneByOneB,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.SEQUENTIAL,
                blockSize = 1
            )
        )
        val oneByOneStrassen = multiply(
            oneByOneA,
            oneByOneB,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.STRASSEN,
                strassenThreshold = 1
            )
        )
        assertMatricesClose(oneByOneSequential, oneByOneStrassen)

        val rowVector = Matrix.fromInitializer(1, 4) { _, c -> (c + 1).toDouble() }
        val colVector = Matrix.fromInitializer(4, 1) { r, _ -> (r + 2).toDouble() }

        val sequential = multiply(
            rowVector,
            colVector,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.SEQUENTIAL,
                blockSize = 2
            )
        )
        val parallel = multiply(
            rowVector,
            colVector,
            MatMulDefaults.default().copy(
                algorithm = MultiplicationAlgorithm.PARALLEL,
                blockSize = 2,
                parallelism = 2
            )
        )
        assertMatricesClose(sequential, parallel)
    }

    @Test
    fun `out-of-core roundtrip preserves dense data`() = runBlocking {
        val matrix = Matrix.fromInitializer(5, 3) { r, c -> (r * 3 + c + 0.5).toDouble() }
        val blockRows = 2
        val blockCols = 2
        val storage = InMemoryBlockStorage()

        writeDenseToBlocks(matrix, blockRows, blockCols, storage)

        val outOfCore = OutOfCoreBlockMatrix(
            totalRows = matrix.numRows.toLong(),
            totalCols = matrix.numCols.toLong(),
            blockRows = blockRows,
            blockCols = blockCols,
            storage = storage
        )

        val dense = materialize(outOfCore, storage)
        assertMatricesClose(matrix, dense)
    }

    private fun assertMatricesClose(expected: Matrix, actual: Matrix, epsilon: Double = 1e-9) {
        assertTrue(
            expected.numRows == actual.numRows && expected.numCols == actual.numCols,
            "Shape mismatch: expected ${expected.numRows}x${expected.numCols}, got ${actual.numRows}x${actual.numCols}"
        )
        for (r in 0 until expected.numRows) {
            for (c in 0 until expected.numCols) {
                val diff = abs(expected[r, c] - actual[r, c])
                assertTrue(
                    diff <= epsilon,
                    "Mismatch at ($r,$c): expected=${expected[r, c]} actual=${actual[r, c]} diff=$diff"
                )
            }
        }
    }

    private suspend fun writeDenseToBlocks(
        matrix: Matrix,
        blockRows: Int,
        blockCols: Int,
        storage: InMemoryBlockStorage
    ) {
        var blockRow = 0
        var row = 0
        while (row < matrix.numRows) {
            val currentRows = minOf(blockRows, matrix.numRows - row)
            var blockCol = 0
            var col = 0
            while (col < matrix.numCols) {
                val currentCols = minOf(blockCols, matrix.numCols - col)
                val data = Array(currentRows) { r ->
                    DoubleArray(currentCols) { c -> matrix[row + r, col + c] }
                }
                storage.write(
                    MatrixBlock(
                        blockRow = blockRow.toLong(),
                        blockCol = blockCol.toLong(),
                        rows = currentRows,
                        cols = currentCols,
                        data = data
                    )
                )
                col += blockCols
                blockCol++
            }
            row += blockRows
            blockRow++
        }
    }

    private suspend fun materialize(
        matrix: OutOfCoreBlockMatrix,
        storage: InMemoryBlockStorage
    ): Matrix {
        val rows = matrix.totalRows.toInt()
        val cols = matrix.totalCols.toInt()
        val dense = DoubleArray(rows * cols)

        val blockRowCount = (rows + matrix.blockRows - 1) / matrix.blockRows
        val blockColCount = (cols + matrix.blockCols - 1) / matrix.blockCols

        for (bi in 0 until blockRowCount) {
            for (bj in 0 until blockColCount) {
                val block = storage.read(bi.toLong(), bj.toLong()) ?: continue
                for (r in 0 until block.rows) {
                    val destBase = (bi * matrix.blockRows + r) * cols + bj * matrix.blockCols
                    for (c in 0 until block.cols) {
                        dense[destBase + c] = block.data[r][c]
                    }
                }
            }
        }

        return Matrix.fromFlat(rows, cols, dense, copy = false)
    }
}
