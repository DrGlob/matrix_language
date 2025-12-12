package org.example.core

import org.example.utils.MatrixValidator

fun Matrix.elementWise(op: (Double, Double) -> Double, other: Matrix): Matrix {
    MatrixValidator.validateDimensionsForElementWise(this, other)
    val result = DoubleArray(rows * cols)
    for (i in result.indices) {
        val r = i / cols
        val c = i % cols
        result[i] = op(this[r, c], other[r, c])
    }
    return Matrix.fromFlat(rows, cols, result, copy = false)
}

fun Matrix.map(transform: (Double) -> Double): Matrix {
    val result = DoubleArray(rows * cols)
    for (i in result.indices) {
        val r = i / cols
        val c = i % cols
        result[i] = transform(this[r, c])
    }
    return Matrix.fromFlat(rows, cols, result, copy = false)
}
