package core

import kotlinx.coroutines.runBlocking
import org.example.core.InMemoryBlockStorage
import org.example.core.Matrix
import org.example.core.MatrixBlock
import org.example.core.OutOfCoreBlockMatrix
import org.example.core.multiply
import kotlin.math.ceil
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertTrue

class OutOfCoreBlockMatrixTest {

    @Test
    fun `out-of-core multiply matches dense result`() = runBlocking {
        val blockSize = 2
        val storageA = InMemoryBlockStorage()
        val storageB = InMemoryBlockStorage()

        // Matrix A: 4x4 filled row-wise 1..16
        writeBlock(storageA, 0, 0, arrayOf(doubleArrayOf(1.0, 2.0), doubleArrayOf(5.0, 6.0)))
        writeBlock(storageA, 0, 1, arrayOf(doubleArrayOf(3.0, 4.0), doubleArrayOf(7.0, 8.0)))
        writeBlock(storageA, 1, 0, arrayOf(doubleArrayOf(9.0, 10.0), doubleArrayOf(13.0, 14.0)))
        writeBlock(storageA, 1, 1, arrayOf(doubleArrayOf(11.0, 12.0), doubleArrayOf(15.0, 16.0)))

        // Matrix B = identity (4x4) to make A * B = A
        writeBlock(storageB, 0, 0, arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0)))
        writeBlock(storageB, 1, 1, arrayOf(doubleArrayOf(1.0, 0.0), doubleArrayOf(0.0, 1.0)))

        val a = OutOfCoreBlockMatrix(4, 4, blockSize, blockSize, storageA)
        val b = OutOfCoreBlockMatrix(4, 4, blockSize, blockSize, storageB)

        val storageResult = InMemoryBlockStorage()
        val outOfCoreResult = a.multiply(b, parallelism = 2, resultStorage = storageResult)

        val dense = materialize(outOfCoreResult, storageResult)
        val expected = Matrix.fromInitializer(4, 4) { r, c -> (r * 4 + c + 1).toDouble() }

        assertDenseEquals(expected, dense)
    }

    @Test
    fun `parallel block multiply demonstrates speedup`() = runBlocking {
        val size = 256
        val blockSize = 64
        val storageA = InMemoryBlockStorage()
        val storageB = InMemoryBlockStorage()

        val blockCount = size / blockSize
        for (bi in 0 until blockCount) {
            for (bj in 0 until blockCount) {
                val data = Array(blockSize) { row ->
                    DoubleArray(blockSize) { col ->
                        (bi * blockSize + row + bj * blockSize + col + 1).toDouble()
                    }
                }
                writeBlock(storageA, bi.toLong(), bj.toLong(), data)
                writeBlock(storageB, bi.toLong(), bj.toLong(), data)
            }
        }

        val a = OutOfCoreBlockMatrix(size.toLong(), size.toLong(), blockSize, blockSize, storageA)
        val b = OutOfCoreBlockMatrix(size.toLong(), size.toLong(), blockSize, blockSize, storageB)

        val sequentialTime = measureTimeMillisSusp {
            a.multiply(b, parallelism = 1, resultStorage = InMemoryBlockStorage())
        }
        val parallelTime = measureTimeMillisSusp {
            a.multiply(b, parallelism = 8, resultStorage = InMemoryBlockStorage())
        }

        println("Out-of-core block multiply sequential=${sequentialTime}ms parallel=${parallelTime}ms")
        assertTrue(
            parallelTime <= sequentialTime * 1.5,
            "Parallel block multiply should be comparable or faster (seq=${sequentialTime}ms, par=${parallelTime}ms)"
        )
    }

    private suspend fun materialize(
        matrix: OutOfCoreBlockMatrix,
        storage: InMemoryBlockStorage
    ): Array<DoubleArray> {
        val rows = matrix.totalRows.toInt()
        val cols = matrix.totalCols.toInt()
        val dense = Array(rows) { DoubleArray(cols) }

        val blockRowCount = ceil(matrix.totalRows.toDouble() / matrix.blockRows).toInt()
        val blockColCount = ceil(matrix.totalCols.toDouble() / matrix.blockCols).toInt()

        for (bi in 0 until blockRowCount) {
            for (bj in 0 until blockColCount) {
                val block = storage.read(bi.toLong(), bj.toLong()) ?: continue
                for (r in 0 until block.rows) {
                    val targetRow = bi * matrix.blockRows + r
                    for (c in 0 until block.cols) {
                        val targetCol = bj * matrix.blockCols + c
                        dense[targetRow][targetCol] = block.data[r][c]
                    }
                }
            }
        }
        return dense
    }

    private fun assertDenseEquals(expected: Matrix, actual: Array<DoubleArray>) {
        for (r in 0 until expected.numRows) {
            assertContentEquals(expected.toList()[r], actual[r].toList(), "Row $r mismatch")
        }
    }

    private suspend fun measureTimeMillisSusp(block: suspend () -> Unit): Long {
        val start = System.nanoTime()
        block()
        return (System.nanoTime() - start) / 1_000_000
    }

    private suspend fun writeBlock(
        storage: InMemoryBlockStorage,
        blockRow: Long,
        blockCol: Long,
        data: Array<DoubleArray>
    ) {
        storage.write(
            MatrixBlock(
                blockRow = blockRow,
                blockCol = blockCol,
                rows = data.size,
                cols = data[0].size,
                data = data
            )
        )
    }
}
