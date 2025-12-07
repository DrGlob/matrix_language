package org.example.core

data class Matrix(val rows: List<List<Double>>) {
    val numRows: Int = rows.size
    val numCols: Int = if (rows.isNotEmpty()) rows[0].size else 0

    init {
        require(rows.all { it.size == numCols }) { "All rows must have the same number of columns" }
    }

    override fun toString(): String {
        return rows.joinToString("\n") { row -> row.joinToString(" ") }
    }
}