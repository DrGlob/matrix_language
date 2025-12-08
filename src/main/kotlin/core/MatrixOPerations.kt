package org.example.core

import org.example.utils.MatrixValidator

fun Matrix.elementWise(op: (Double, Double) -> Double, other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForElementWise(this, other)
    return Matrix(rows.zip(other.rows) { row1, row2 ->
        row1.zip(row2, op)
    })
}

fun Matrix.map(transform: (Double) -> Double): Matrix {
    return Matrix(rows.map { row -> row.map(transform) })
}
