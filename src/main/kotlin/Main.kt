package org.example

import kotlin.math.max
import kotlin.math.min

// First, define a simple immutable Matrix class
data class Matrix(val rows: List<List<Double>>) {
    val numRows: Int = rows.size
    val numCols: Int = if (rows.isNotEmpty()) rows[0].size else 0

    init {
        require(rows.all { it.size == numCols }) { "All rows must have the same number of columns" }
    }

    // Operator overloads for basic operations
    operator fun plus(other: Matrix): Matrix {
        require(numRows == other.numRows && numCols == other.numCols) { "Matrices must have the same dimensions for addition" }
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a + b }
        })
    }

    operator fun minus(other: Matrix): Matrix {
        require(numRows == other.numRows && numCols == other.numCols) { "Matrices must have the same dimensions for subtraction" }
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2) { a, b -> a - b }
        })
    }

    operator fun times(scalar: Double): Matrix {
        return Matrix(rows.map { row -> row.map { it * scalar } })
    }

    operator fun times(other: Matrix): Matrix {
        require(numCols == other.numRows) { "Number of columns in first matrix must equal number of rows in second" }
        return Matrix((0 until numRows).map { i ->
            (0 until other.numCols).map { j ->
                (0 until numCols).sumOf { k -> rows[i][k] * other.rows[k][j] }
            }
        })
    }

    fun transpose(): Matrix {
        return Matrix((0 until numCols).map { j ->
            (0 until numRows).map { i -> rows[i][j] }
        })
    }

    fun elementWise(op: (Double, Double) -> Double, other: Matrix): Matrix {
        require(numRows == other.numRows && numCols == other.numCols) { "Matrices must have the same dimensions" }
        return Matrix(rows.zip(other.rows) { row1, row2 ->
            row1.zip(row2, op)
        })
    }

    fun map(transform: (Double) -> Double): Matrix {
        return Matrix(rows.map { row -> row.map(transform) })
    }

    override fun toString(): String {
        return rows.joinToString("\n") { row -> row.joinToString(" ") }
    }
}

// Factory functions for creating matrices
fun matrixOf(vararg rows: List<Double>): Matrix = Matrix(rows.toList())
fun zeros(rows: Int, cols: Int): Matrix = Matrix(List(rows) { List(cols) { 0.0 } })
fun ones(rows: Int, cols: Int): Matrix = Matrix(List(rows) { List(cols) { 1.0 } })
fun identity(size: Int): Matrix = Matrix(List(size) { i -> List(size) { if (it == i) 1.0 else 0.0 } })

// Now, define a functional DSL context for matrix computations
class MatrixContext {
    private val variables: MutableMap<String, Matrix> = mutableMapOf()

    // Declare a variable
    fun declare(name: String, value: Matrix) {
        variables[name] = value
        println("Declared $name = $value")
    }

    // Get a variable
    operator fun get(name: String): Matrix {
        return variables[name] ?: throw IllegalArgumentException("Variable $name not found")
    }

    // Functional if-else as an expression
    fun <T> ifThen(condition: () -> Boolean, thenBlock: () -> T, elseBlock: () -> T): T {
        return if (condition()) thenBlock() else elseBlock()
    }

    // Functional loop: recursive loop with condition and body that returns a value
    tailrec fun <T> loop(
        condition: (T) -> Boolean,
        update: (T) -> T,
        initial: T,
        body: (T) -> T
    ): T {
        return if (condition(initial)) {
            val newValue = body(initial)
            loop(condition, update, update(newValue), body)
        } else {
            initial
        }
    }

    // Fold over a range (functional equivalent of for-loop)
    fun <T> foldRange(start: Int, end: Int, initial: T, op: (T, Int) -> T): T {
        return (start until end).fold(initial, op)
    }

    // Map over a range
    fun mapRange(start: Int, end: Int, transform: (Int) -> Double): List<Double> {
        return (start until end).map(transform)
    }

    // To execute the computation and get a result
    fun compute(block: MatrixContext.() -> Matrix): Matrix {
        return block()
    }
}

// Top-level function to run the DSL
fun matrixComputation(block: MatrixContext.() -> Matrix): Matrix {
    val context = MatrixContext()
    return context.compute(block)
}

// Example usage of the DSL
fun main() {
    val result = matrixComputation {
        declare("A", matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
        declare("B", matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0)))

        // Debug output
        println("Matrix A dimensions: ${this["A"].numRows}x${this["A"].numCols}")
        println("Matrix B dimensions: ${this["B"].numRows}x${this["B"].numCols}")

        // Conditional: if numRows > 1, add A + B, else A * B
        val conditionalResult = ifThen(
            { this["A"].numRows > 1 },
            {
                println("Condition true: adding A + B")
                this["A"] + this["B"]
            },
            {
                println("Condition false: multiplying A * B")
                this["A"] * this["B"]
            }
        )
        println("Conditional result dimensions: ${conditionalResult.numRows}x${conditionalResult.numCols}")

        // Functional loop: sum identity matrices
        val loopResult = loop(
            condition = { it < 3 },
            update = { it + 1 },
            initial = 0,
            body = { i ->
                val identity = identity(2)
                if (i == 0) {
                    declare("Sum", identity)
                } else {
                    declare("Sum", this["Sum"] + identity)
                }
                i
            }
        )

        // Create a vector of compatible size for multiplication
        val vectorList = foldRange(0, 2, emptyList<Double>()) { acc, i ->
            acc + listOf(i * 1.0)
        }
        val vector = Matrix(listOf(vectorList))
        println("Vector dimensions: ${vector.numRows}x${vector.numCols}")
        println("Vector transpose dimensions: ${vector.transpose().numRows}x${vector.transpose().numCols}")

        // Multiply conditionalResult (2x2) by vector transpose (2x1) - should work
        val finalResult = conditionalResult * vector.transpose()
        println("Final multiplication: (${conditionalResult.numRows}x${conditionalResult.numCols}) * (${vector.transpose().numRows}x${vector.transpose().numCols})")

        finalResult
    }

    println("\nFinal Result:")
    println(result)
}