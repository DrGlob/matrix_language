package org.example.core

import kotlin.math.min

/**
 * Представление матрицы, разбитой на блоки фиксированного размера.
 * Блоки — лишь представления (view) над исходной матрицей без копирования данных.
 */
class BlockMatrix internal constructor(
    val source: Matrix,
    val blockSize: Int = DEFAULT_BLOCK_SIZE
) {
    val rows: Int = source.numRows
    val cols: Int = source.numCols
    val blockRows: Int
    val blockCols: Int
    val blocks: List<List<BlockView>>

    init {
        blockRows = (rows + blockSize - 1) / blockSize
        blockCols = (cols + blockSize - 1) / blockSize

        blocks = List(blockRows) { br ->
            List(blockCols) { bc ->
                val rowStart = br * blockSize
                val colStart = bc * blockSize
                val height = min(blockSize, rows - rowStart)
                val width = min(blockSize, cols - colStart)
                BlockView(source, rowStart, colStart, height, width)
            }
        }
    }

    /**
     * Полная материализация в обычную Matrix (с копированием данных).
     */
    fun toMatrix(): Matrix = fromBlocks(blocks.map { row -> row.map { it.toMatrix() } })

    companion object {
        const val DEFAULT_BLOCK_SIZE = 128

        /**
         * Сборка матрицы из сетки блоков.
         * Предполагается, что высоты блоков согласованы по строкам, а ширины — по столбцам.
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
}

// Удобный алиас, чтобы использовать формулировку BlockGrid
typealias BlockGrid = BlockMatrix

// Базовый размер блока по умолчанию, доступный снаружи
const val BLOCK_SIZE: Int = BlockMatrix.DEFAULT_BLOCK_SIZE

/**
 * Сборка матрицы из сетки блоков (удобный внешний API).
 */
fun fromBlocks(grid: BlockGrid): Matrix = grid.toMatrix()

/**
 * Представление блока матрицы без копирования данных.
 */
class BlockView internal constructor(
    private val source: Matrix,
    private val rowStart: Int,
    private val colStart: Int,
    val rows: Int,
    val cols: Int
) {
    val numRows: Int get() = rows
    val numCols: Int get() = cols

    operator fun get(row: Int, col: Int): Double {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: ($row, $col) for block ${rows}x$cols"
        }
        return source[rowStart + row, colStart + col]
    }

    fun toMatrix(): Matrix =
        Matrix.fromInitializer(rows, cols) { r, c -> source[rowStart + r, colStart + c] }
}

fun Matrix.toBlocks(blockSize: Int = BlockMatrix.DEFAULT_BLOCK_SIZE): BlockMatrix =
    BlockMatrix(this, blockSize)
