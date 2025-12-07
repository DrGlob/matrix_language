package org.example.core

object MatrixFactory {
    fun create(vararg rows: List<Double>): Matrix = Matrix(rows.toList())

    fun zeros(rows: Int, cols: Int): Matrix =
        Matrix(List(rows) { List(cols) { 0.0 } })

    fun ones(rows: Int, cols: Int): Matrix =
        Matrix(List(rows) { List(cols) { 1.0 } })

    fun identity(size: Int): Matrix =
        Matrix(List(size) { i -> List(size) { if (it == i) 1.0 else 0.0 } })

    fun diagonal(vararg values: Double): Matrix {
        val size = values.size
        return Matrix(List(size) { i ->
            List(size) { j -> if (i == j) values[i] else 0.0 }
        })
    }
}

// Extension-функции для удобства
fun matrixOf(vararg rows: List<Double>): Matrix = MatrixFactory.create(*rows)
fun zeros(rows: Int, cols: Int): Matrix = MatrixFactory.zeros(rows, cols)
fun ones(rows: Int, cols: Int): Matrix = MatrixFactory.ones(rows, cols)
fun identity(size: Int): Matrix = MatrixFactory.identity(size)