package org.example.core

import org.example.utils.MatrixValidator

operator fun Matrix.plus(other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForAddition(this, other)
    return Matrix(rows.zip(other.rows) { row1, row2 ->
        row1.zip(row2) { a, b -> a + b }
    })
}

operator fun Matrix.minus(other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForSubtraction(this, other)
    return Matrix(rows.zip(other.rows) { row1, row2 ->
        row1.zip(row2) { a, b -> a - b }
    })
}

operator fun Matrix.times(scalar: Double): Matrix {
    return Matrix(rows.map { row -> row.map { it * scalar } })
}

operator fun Matrix.times(other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForMultiplication(this, other)
    return Matrix((0 until numRows).map { i ->
        (0 until other.numCols).map { j ->
            (0 until numCols).sumOf { k -> rows[i][k] * other.rows[k][j] }
        }
    })
}

fun Matrix.transpose(): Matrix {
    return Matrix((0 until numCols).map { j ->
        (0 until numRows).map { i -> rows[i][j] }
    })
}

fun Matrix.elementWise(op: (Double, Double) -> Double, other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForElementWise(this, other)
    return Matrix(rows.zip(other.rows) { row1, row2 ->
        row1.zip(row2, op)
    })
}

fun Matrix.map(transform: (Double) -> Double): Matrix {
    return Matrix(rows.map { row -> row.map(transform) })
}