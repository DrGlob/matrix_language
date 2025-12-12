package org.example.extensions

import org.example.core.Matrix
import org.example.core.elementWise
import org.example.core.map
import kotlin.math.pow

// Расширения для дополнительных операций
fun Matrix.hadamardProduct(other: Matrix): Matrix =
    elementWise({ a, b -> a * b }, other)

fun Matrix.pow(n: Int): Matrix =
    map { it.pow(n) }

fun Matrix.sum(): Double =
    (0 until rows * cols).sumOf { idx ->
        val r = idx / cols
        val c = idx % cols
        this[r, c]
    }

fun Matrix.mean(): Double =
    sum() / (numRows * numCols)

// Нормализация
fun Matrix.normalize(): Matrix {
    var maxVal = Double.NEGATIVE_INFINITY
    var minVal = Double.POSITIVE_INFINITY
    for (r in 0 until numRows) {
        for (c in 0 until numCols) {
            val v = this[r, c]
            if (v > maxVal) maxVal = v
            if (v < minVal) minVal = v
        }
    }
    if (maxVal == Double.NEGATIVE_INFINITY) {
        maxVal = 0.0
        minVal = 0.0
    }
    if (maxVal == minVal) {
        // Avoid division by zero for constant matrices
        return Matrix.fromFlat(numRows, numCols, DoubleArray(numRows * numCols), copy = false)
    }
    return map { (it - minVal) / (maxVal - minVal) }
}
