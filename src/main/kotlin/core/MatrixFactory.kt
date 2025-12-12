package org.example.core

object MatrixFactory {
    fun create(vararg rows: List<Double>): Matrix = Matrix(rows.toList())

    fun zeros(rows: Int, cols: Int): Matrix = Matrix.zeros(rows, cols)

    fun ones(rows: Int, cols: Int): Matrix =
        Matrix.fromFlat(rows, cols, DoubleArray(rows * cols) { 1.0 }, copy = false)

    fun identity(size: Int): Matrix = Matrix.identity(size)

    fun diagonal(vararg values: Double): Matrix {
        val size = values.size
        val data = DoubleArray(size * size)
        for (i in values.indices) {
            data[i * size + i] = values[i]
        }
        return Matrix.fromFlat(size, size, data, copy = false)
    }
}

// Extension-функции для удобства
fun matrixOf(vararg rows: List<Double>): Matrix = MatrixFactory.create(*rows)
fun zeros(rows: Int, cols: Int): Matrix = MatrixFactory.zeros(rows, cols)
fun ones(rows: Int, cols: Int): Matrix = MatrixFactory.ones(rows, cols)
fun identity(size: Int): Matrix = MatrixFactory.identity(size)
