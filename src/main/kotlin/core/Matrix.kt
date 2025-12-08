package org.example.core

import org.example.utils.MatrixValidator

interface MatrixOperations {
    operator fun plus(other: Matrix): Matrix
    operator fun minus(other: Matrix): Matrix
    operator fun times(scalar: Double): Matrix
    operator fun times(other: Matrix): Matrix
    fun transpose(): Matrix
}

// Делаем Matrix реализацией этого интерфейса
class Matrix private constructor(private val safeRows: List<List<Double>>) : MatrixOperations {
    val rows: List<List<Double>> = safeRows
    val numRows: Int = safeRows.size
    val numCols: Int = if (safeRows.isNotEmpty()) safeRows[0].size else 0

    init {
        require(safeRows.all { it.size == numCols }) {
            "All rows must have the same number of columns"
        }
    }

    companion object {
        operator fun invoke(rows: List<List<Double>>): Matrix {
            // Defensive copy to prevent external mutation of provided lists
            val copiedRows = rows.map { it.toList() }
            return Matrix(copiedRows)
        }
    }

    // Реализация операций
    override operator fun plus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForAddition(this, other)
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a + b }
        })
    }

    override operator fun minus(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForSubtraction(this, other)
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a - b }
        })
    }

    override operator fun times(scalar: Double): Matrix {
        return Matrix(rows.map { row -> row.map { it * scalar } })
    }

    override operator fun times(other: Matrix): Matrix {
        MatrixValidator.validateDimensionsForMultiplication(this, other)
        return Matrix((0 until numRows).map { i ->
            (0 until other.numCols).map { j ->
                (0 until numCols).sumOf { k -> rows[i][k] * other.rows[k][j] }
            }
        })
    }

    override fun transpose(): Matrix {
        return Matrix((0 until numCols).map { j ->
            (0 until numRows).map { i -> rows[i][j] }
        })
    }

    // Вспомогательные методы
    operator fun get(row: Int, col: Int): Double = rows[row][col]

    operator fun set(row: Int, col: Int, value: Double): Matrix {
        val newRows = rows.toMutableList().map { it.toMutableList() }
        newRows[row][col] = value
        return Matrix(newRows)
    }

    override fun equals(other: Any?): Boolean =
        other is Matrix && rows == other.rows

    override fun hashCode(): Int = rows.hashCode()

    override fun toString(): String {
        return rows.joinToString("\n") { row -> row.joinToString(" ") }
    }
}
