package org.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.ConcurrentHashMap

/**
 * Потоковое блочное представление огромных матриц.
 * Блоки загружаются из BlockStorage по мере необходимости, результат записывается обратно в BlockStorage.
 */
data class MatrixBlock(
    val blockRow: Long,
    val blockCol: Long,
    val rows: Int,
    val cols: Int,
    val data: Array<DoubleArray> // row-major внутри блока
)

interface BlockStorage {
    suspend fun read(blockRow: Long, blockCol: Long): MatrixBlock?
    suspend fun write(block: MatrixBlock)
}

/**
 * Простое in-memory хранилище блоков (для тестов и малых объёмов).
 */
class InMemoryBlockStorage : BlockStorage {
    private val map = ConcurrentHashMap<Pair<Long, Long>, MatrixBlock>()

    override suspend fun read(blockRow: Long, blockCol: Long): MatrixBlock? =
        map[blockRow to blockCol]

    override suspend fun write(block: MatrixBlock) {
        map[block.blockRow to block.blockCol] = block
    }
}

/**
 * Out-of-core блоковая матрица: оперирует блоками, не требуя материализации всей матрицы в память.
 */
class OutOfCoreBlockMatrix(
    val totalRows: Long,
    val totalCols: Long,
    val blockRows: Int,
    val blockCols: Int,
    val storage: BlockStorage
)

/**
 * Блочное умножение, работа с блоками через BlockStorage.
 */
suspend fun OutOfCoreBlockMatrix.multiply(
    other: OutOfCoreBlockMatrix,
    parallelism: Int = MatMulDefaults.default().parallelism,
    resultStorage: BlockStorage = InMemoryBlockStorage(),
    progress: ((doneBlocks: Long, totalBlocks: Long) -> Unit)? = null
): OutOfCoreBlockMatrix = coroutineScope {
    require(this@multiply.totalCols == other.totalRows) {
        "Incompatible matrices: ${this@multiply.totalRows}x${this@multiply.totalCols} cannot be multiplied by ${other.totalRows}x${other.totalCols}"
    }

    val resBlockRows = divideCeil(totalRows, blockRows.toLong())
    val resBlockCols = divideCeil(other.totalCols, other.blockCols.toLong())
    val sharedBlocks = divideCeil(totalCols, blockCols.toLong())
    val semaphore = Semaphore(parallelism)

    val totalTasks = resBlockRows * resBlockCols
    var done = 0L

    val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

    for (bi in 0 until resBlockRows) {
        for (bj in 0 until resBlockCols) {
            jobs.add(async(Dispatchers.Default) {
                semaphore.withPermit {
                    val blockRowSize = lastBlockSize(totalRows, blockRows, bi)
                    val blockColSize = lastBlockSize(other.totalCols, other.blockCols, bj)
                    val local = Array(blockRowSize) { DoubleArray(blockColSize) }

                    for (bk in 0 until sharedBlocks) {
                        val a = storage.read(bi, bk) ?: continue
                        val b = other.storage.read(bk, bj) ?: continue
                        multiplyAndAccumulate(a, b, local)
                    }

                    resultStorage.write(
                        MatrixBlock(
                            blockRow = bi,
                            blockCol = bj,
                            rows = blockRowSize,
                            cols = blockColSize,
                            data = local
                        )
                    )

                    val current = synchronized(this@multiply) { ++done }
                    progress?.invoke(current, totalTasks)
                }
                Unit
            })
        }
    }

    jobs.forEach { it.await() }

    OutOfCoreBlockMatrix(
        totalRows = totalRows,
        totalCols = other.totalCols,
        blockRows = blockRows,
        blockCols = other.blockCols,
        storage = resultStorage
    )
}

private fun multiplyAndAccumulate(a: MatrixBlock, b: MatrixBlock, acc: Array<DoubleArray>) {
    require(a.cols == b.rows) { "Block dimensions mismatch: ${a.rows}x${a.cols} vs ${b.rows}x${b.cols}" }
    val rows = a.rows
    val cols = b.cols
    val shared = a.cols

    var r = 0
    while (r < rows) {
        val accRow = acc[r]
        var c = 0
        while (c < cols) {
            var sum = accRow[c]
            var k = 0
            while (k < shared) {
                sum += a.data[r][k] * b.data[k][c]
                k++
            }
            accRow[c] = sum
            c++
        }
        r++
    }
}

private fun divideCeil(total: Long, part: Long): Long =
    (total + part - 1) / part

private fun lastBlockSize(total: Long, blockSize: Int, blockIndex: Long): Int {
    val start = blockIndex * blockSize
    val remaining = total - start
    return if (remaining >= blockSize) blockSize else remaining.toInt()
}
