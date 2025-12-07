package org.example.utils

import org.example.core.Matrix

fun Matrix.printInfo(label: String = "") {
    val prefix = if (label.isNotEmpty()) "$label: " else ""
    println("${prefix}Dimensions: ${numRows}x${numCols}")
    println("${prefix}Content:")
    println(this)
    println()
}

fun Matrix.toFormattedString(decimalPlaces: Int = 2): String {
    val format = "%.${decimalPlaces}f"
    return rows.joinToString("\n") { row ->
        row.joinToString(" ") { format.format(it) }
    }
}