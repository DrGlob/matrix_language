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
    rows.sumOf { row -> row.sum() }

fun Matrix.mean(): Double =
    sum() / (numRows * numCols)

// Нормализация
fun Matrix.normalize(): Matrix {
    val mean = mean()
    val maxVal = rows.flatten().maxOrNull() ?: 0.0
    val minVal = rows.flatten().minOrNull() ?: 0.0
    if (maxVal == minVal) {
        // Avoid division by zero for constant matrices
        return Matrix(List(numRows) { List(numCols) { 0.0 } })
    }
    return map { (it - minVal) / (maxVal - minVal) }
}
