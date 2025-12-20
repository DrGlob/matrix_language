package org.example.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlin.math.min

/**
 * Блочная матрица: прямоугольная сетка вложенных матриц.
 * Блоки хранятся как отдельные Matrix, без ссылок на единую плоскую матрицу.
 * Конвертация в обычную Matrix нужна только для явной материализации/вывода.
 */
class BlockMatrix internal constructor(
    val blocks: List<List<Matrix>>,
    val blockSize: Int = DEFAULT_BLOCK_SIZE
) {
    val blockRows: Int
    val blockCols: Int
    val rows: Int
    val cols: Int
    private val rowHeights: IntArray
    private val colWidths: IntArray

    init {
        require(blocks.isNotEmpty()) { "Blocks must not be empty" }
        blockRows = blocks.size
        blockCols = blocks[0].size
        require(blockCols > 0) { "Blocks must contain at least one column" }
        require(blocks.all { it.size == blockCols }) { "All block rows must have the same number of columns" }

        rowHeights = IntArray(blockRows)
        for (br in 0 until blockRows) {
            val expectedHeight = blocks[br][0].numRows
            require(blocks[br].all { it.numRows == expectedHeight }) {
                "Inconsistent block heights in block row $br"
            }
            rowHeights[br] = expectedHeight
        }

        colWidths = IntArray(blockCols)
        for (bc in 0 until blockCols) {
            val expectedWidth = blocks[0][bc].numCols
            for (br in 0 until blockRows) {
                require(blocks[br][bc].numCols == expectedWidth) {
                    "Inconsistent block widths in block column $bc"
                }
            }
            colWidths[bc] = expectedWidth
        }

        rows = rowHeights.sum()
        cols = colWidths.sum()
    }

    /**
     * Полная материализация в обычную Matrix (с копированием данных).
     */
    fun toMatrix(): Matrix = fromBlocks(blocks, blockSize)

    operator fun plus(other: BlockMatrix): BlockMatrix {
        requireSameShape(other, "add")
        val sumBlocks = blocks.mapIndexed { br, row ->
            row.mapIndexed { bc, block ->
                block + other.blocks[br][bc]
            }
        }
        return BlockMatrix(sumBlocks, min(blockSize, other.blockSize))
    }

    operator fun minus(other: BlockMatrix): BlockMatrix {
        requireSameShape(other, "subtract")
        val diffBlocks = blocks.mapIndexed { br, row ->
            row.mapIndexed { bc, block ->
                block - other.blocks[br][bc]
            }
        }
        return BlockMatrix(diffBlocks, min(blockSize, other.blockSize))
    }

    /**
     * Умножение блоковых матриц без промежуточной материализации в плоскую Matrix.
     * Для STRASSEN выполняется блочное умножение; Strassen для блоков не применяется.
     */
    fun multiply(other: BlockMatrix, config: MatMulConfig = MatMulDefaults.default()): BlockMatrix {
        validateGridCompatibility(other)

        return when (config.algorithm) {
            MultiplicationAlgorithm.PARALLEL ->
                runBlocking { multiplyParallel(other, config.parallelism) }
            else -> multiplySequential(other)
        }
    }

    private fun multiplySequential(other: BlockMatrix): BlockMatrix {
        val result = Array(blockRows) { arrayOfNulls<Matrix>(other.blockCols) }
        for (br in 0 until blockRows) {
            for (bc in 0 until other.blockCols) {
                result[br][bc] = multiplyBlock(br, bc, other)
            }
        }
        return BlockMatrix(result.map { it.filterNotNull() }, blockSize)
    }

    private suspend fun multiplyParallel(other: BlockMatrix, parallelism: Int): BlockMatrix = coroutineScope {
        val semaphore = Semaphore(parallelism)
        val result = Array(blockRows) { arrayOfNulls<Matrix>(other.blockCols) }
        val jobs = mutableListOf<kotlinx.coroutines.Deferred<Unit>>()

        for (br in 0 until blockRows) {
            for (bc in 0 until other.blockCols) {
                val job = async(Dispatchers.Default) {
                    semaphore.withPermit {
                        result[br][bc] = multiplyBlock(br, bc, other)
                    }
                }
                jobs.add(job)
            }
        }

        jobs.forEach { it.await() }
        BlockMatrix(result.map { it.filterNotNull() }, blockSize)
    }

    private fun multiplyBlock(br: Int, bc: Int, other: BlockMatrix): Matrix {
        val resRows = rowHeights[br]
        val resCols = other.colWidths[bc]
        val local = DoubleArray(resRows * resCols)

        for (bk in 0 until blockCols) {
            val a = blocks[br][bk]
            val b = other.blocks[bk][bc]
            val aRows = a.numRows
            val aCols = a.numCols
            val bCols = b.numCols
            require(aCols == b.numRows) { "Block dimensions mismatch at k=$bk: ${aRows}x${aCols} vs ${b.numRows}x${bCols}" }

            val aData = a.data
            val bData = b.data

            var r = 0
            while (r < aRows) {
                val localBase = r * resCols
                var c = 0
                while (c < bCols) {
                    var acc = local[localBase + c]
                    var k = 0
                    while (k < aCols) {
                        acc += aData[r * aCols + k] * bData[k * bCols + c]
                        k++
                    }
                    local[localBase + c] = acc
                    c++
                }
                r++
            }
        }

        return Matrix.fromFlat(resRows, resCols, local, copy = false)
    }

    companion object {
        const val DEFAULT_BLOCK_SIZE = MatMulDefaults.DEFAULT_BLOCK_SIZE

        /**
         * Сборка матрицы из сетки блоков.
         * Высоты блоков согласованы по строкам, ширины — по столбцам.
         */
        fun fromBlocks(blocks: List<List<Matrix>>, blockSize: Int = DEFAULT_BLOCK_SIZE): Matrix {
            require(blocks.isNotEmpty()) { "Blocks must not be empty" }
            val blockRows = blocks.size
            val blockCols = blocks[0].size
            require(blockCols > 0) { "Blocks must contain at least one column" }
            require(blocks.all { it.size == blockCols }) { "All block rows must have the same number of columns" }

            val rowHeights = IntArray(blockRows)
            for (br in 0 until blockRows) {
                val expectedHeight = blocks[br][0].numRows
                require(blocks[br].all { it.numRows == expectedHeight }) {
                    "Inconsistent block heights in block row $br"
                }
                rowHeights[br] = expectedHeight
            }

            val colWidths = IntArray(blockCols)
            for (bc in 0 until blockCols) {
                val expectedWidth = blocks[0][bc].numCols
                for (br in 0 until blockRows) {
                    require(blocks[br][bc].numCols == expectedWidth) {
                        "Inconsistent block widths in block column $bc"
                    }
                }
                colWidths[bc] = expectedWidth
            }

            val totalRows = rowHeights.sum()
            val totalCols = colWidths.sum()
            val data = DoubleArray(totalRows * totalCols)

            var rowOffset = 0
            for (br in 0 until blockRows) {
                var colOffset = 0
                for (bc in 0 until blockCols) {
                    val block = blocks[br][bc]
                    for (r in 0 until block.numRows) {
                        val destBase = (rowOffset + r) * totalCols + colOffset
                        for (c in 0 until block.numCols) {
                            data[destBase + c] = block[r, c]
                        }
                    }
                    colOffset += block.numCols
                }
                rowOffset += blocks[br][0].numRows
            }

            return Matrix.fromFlat(totalRows, totalCols, data, copy = false)
        }
    }

    private fun validateGridCompatibility(other: BlockMatrix) {
        require(cols == other.rows) {
            "Cannot multiply block matrices of different shapes: ${rows}x${cols} vs ${other.rows}x${other.cols}"
        }
        require(blockCols == other.blockRows) {
            "Block grid shapes are incompatible: ${blockRows}x$blockCols vs ${other.blockRows}x${other.blockCols}"
        }
        for (bk in 0 until blockCols) {
            val width = colWidths[bk]
            val height = other.rowHeights[bk]
            require(width == height) {
                "Inner block dimensions mismatch at k=$bk: $width vs $height"
            }
        }
    }

    private fun requireSameShape(other: BlockMatrix, op: String) {
        require(rows == other.rows && cols == other.cols) {
            "Cannot $op block matrices of different shapes: ${rows}x${cols} vs ${other.rows}x${other.cols}"
        }
        require(blockRows == other.blockRows && blockCols == other.blockCols) {
            "Block grids must match for $op: ${blockRows}x$blockCols vs ${other.blockRows}x${other.blockCols}"
        }
        for (i in 0 until blockRows) {
            require(rowHeights[i] == other.rowHeights[i]) { "Row block heights differ at row $i" }
        }
        for (j in 0 until blockCols) {
            require(colWidths[j] == other.colWidths[j]) { "Column block widths differ at col $j" }
        }
    }
}

// Удобный алиас, чтобы использовать формулировку BlockGrid
typealias BlockGrid = BlockMatrix

// Базовый размер блока по умолчанию, доступный снаружи
const val BLOCK_SIZE: Int = BlockMatrix.DEFAULT_BLOCK_SIZE

/**
 * Сборка матрицы из сетки блоков (удобный внешний API).
 */
fun fromBlocks(grid: BlockGrid): Matrix = grid.toMatrix()

fun Matrix.toBlocks(blockSize: Int = BlockMatrix.DEFAULT_BLOCK_SIZE): BlockMatrix {
    val blockRows = (numRows + blockSize - 1) / blockSize
    val blockCols = (numCols + blockSize - 1) / blockSize
    val blocks = List(blockRows) { br ->
        List(blockCols) { bc ->
            val rowStart = br * blockSize
            val colStart = bc * blockSize
            val height = min(blockSize, numRows - rowStart)
            val width = min(blockSize, numCols - colStart)
            Matrix.fromInitializer(height, width) { r, c ->
                this[rowStart + r, colStart + c]
            }
        }
    }
    return BlockMatrix(blocks, blockSize)
}
