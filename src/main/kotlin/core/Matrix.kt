package org.example.core

import org.example.utils.MatrixValidator

interface MatrixOperations {
    operator fun plus(other: Matrix): Matrix
    operator fun minus(other: Matrix): Matrix
    operator fun times(scalar: Double): Matrix
    operator fun times(other: Matrix): Matrix
    fun transpose(): Matrix
}

// Иммутабельная матрица с плоским хранением (row-major)
class Matrix private constructor(
    internal val data: DoubleArray,
    val rows: Int,
    val cols: Int
) : MatrixOperations {

    val numRows: Int get() = rows
    val numCols: Int get() = cols

    companion object {
        operator fun invoke(rows: List<List<Double>>): Matrix =
            fromFlat(rows.size, if (rows.isNotEmpty()) rows[0].size else 0, flatten(rows), copy = false)

        fun fromInitializer(rows: Int, cols: Int, init: (Int, Int) -> Double): Matrix {
            val data = DoubleArray(rows * cols)
            var idx = 0
            for (r in 0 until rows) {
                for (c in 0 until cols) {
                    data[idx++] = init(r, c)
                }
            }
            return Matrix(data, rows, cols)
        }

        fun zeros(rows: Int, cols: Int): Matrix =
            Matrix(DoubleArray(rows * cols), rows, cols)

        fun identity(size: Int): Matrix =
            fromInitializer(size, size) { r, c -> if (r == c) 1.0 else 0.0 }

        fun fromFlat(rows: Int, cols: Int, data: DoubleArray, copy: Boolean = true): Matrix {
            require(rows >= 0 && cols >= 0) { "Matrix dimensions must be non-negative" }
            require(data.size == rows * cols) {
                "Data size (${data.size}) does not match dimensions ${rows}x$cols"
            }
            return Matrix(if (copy) data.copyOf() else data, rows, cols)
        }

        private fun flatten(rows: List<List<Double>>): DoubleArray {
            val rowCount = rows.size
            val colCount = if (rowCount > 0) rows[0].size else 0
            require(rows.all { it.size == colCount }) { "All rows must have the same number of columns" }

            val flat = DoubleArray(rowCount * colCount)
            var idx = 0
            for (r in 0 until rowCount) {
                val row = rows[r]
                for (c in 0 until colCount) {
                    flat[idx++] = row[c]
                }
            }
            return flat
        }
    }

    private fun index(row: Int, col: Int): Int {
        require(row in 0 until rows && col in 0 until cols) {
            "Index out of bounds: ($row, $col) for matrix ${rows}x$cols"
        }
        return row * cols + col
    }

    operator fun get(row: Int, col: Int): Double = data[index(row, col)]

    operator fun set(row: Int, col: Int, value: Double): Matrix {
        val copy = data.copyOf()
        copy[index(row, col)] = value
        return Matrix(copy, rows, cols)
    }

    fun toList(): List<List<Double>> =
        (0 until rows).map { r ->
            (0 until cols).map { c -> data[r * cols + c] }
        }

    // Реализация операций
    override operator fun plus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForAddition(this, other)
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] + other.data[i]
        }
        return Matrix(result, rows, cols)
    }

    override operator fun minus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForSubtraction(this, other)
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] - other.data[i]
        }
        return Matrix(result, rows, cols)
    }

    override operator fun times(scalar: Double): Matrix {
        val result = DoubleArray(rows * cols)
        for (i in result.indices) {
            result[i] = data[i] * scalar
        }
        return Matrix(result, rows, cols)
    }

    override operator fun times(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForMultiplication(this, other)
        val result = DoubleArray(rows * other.cols)
        var idx = 0
        for (i in 0 until rows) {
            val rowOffset = i * cols
            for (j in 0 until other.cols) {
                var sum = 0.0
                var k = 0
                while (k < cols) {
                    sum += data[rowOffset + k] * other.data[k * other.cols + j]
                    k++
                }
                result[idx++] = sum
            }
        }
        return Matrix(result, rows, other.cols)
    }

    override fun transpose(): Matrix {
        val result = DoubleArray(cols * rows)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                result[c * rows + r] = data[r * cols + c]
            }
        }
        return Matrix(result, cols, rows)
    }

    override fun equals(other: Any?): Boolean =
        other is Matrix &&
                rows == other.rows &&
                cols == other.cols &&
                data.contentEquals(other.data)

    override fun hashCode(): Int = 31 * rows + 31 * cols + data.contentHashCode()

    override fun toString(): String {
        return (0 until rows).joinToString("\n") { r ->
            (0 until cols).joinToString(" ") { c -> data[r * cols + c].toString() }
        }
    }
}
