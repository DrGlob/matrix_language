package org.example.core

interface MatrixOperations {
    operator fun plus(other: Matrix): Matrix
    operator fun minus(other: Matrix): Matrix
    operator fun times(scalar: Double): Matrix
    operator fun times(other: Matrix): Matrix
    fun transpose(): Matrix
}

// Делаем Matrix реализацией этого интерфейса
data class Matrix(val rows: List<List<Double>>) : MatrixOperations {
    val numRows: Int = rows.size
    val numCols: Int = if (rows.isNotEmpty()) rows[0].size else 0

    init {
        require(rows.all { it.size == numCols }) {
            "All rows must have the same number of columns"
        }
    }

    // Реализация операций
    override operator fun plus(other: Matrix): Matrix {
        require(numRows == other.numRows && numCols == other.numCols) {
            "Matrices must have the same dimensions for addition"
        }
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a + b }
        })
    }

    override operator fun minus(other: Matrix): Matrix {
        require(numRows == other.numRows && numCols == other.numCols) {
            "Matrices must have the same dimensions for subtraction"
        }
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a - b }
        })
    }

    override operator fun times(scalar: Double): Matrix {
        return Matrix(rows.map { row -> row.map { it * scalar } })
    }

    override operator fun times(other: Matrix): Matrix {
        require(numCols == other.numRows) {
            "Number of columns in first matrix must equal number of rows in second"
        }
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

    override fun toString(): String {
        return rows.joinToString("\n") { row -> row.joinToString(" ") }
    }
}