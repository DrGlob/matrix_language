package org.example.dsl

class ControlFlow(private val context: MatrixContext) {
    // Functional if-else as an expression
    fun <T> ifThen(
        condition: () -> Boolean,
        thenBlock: () -> T,
        elseBlock: () -> T
    ): T {
        return if (condition()) thenBlock() else elseBlock()
    }

    // Functional loop with tail recursion
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

    // Functional for-loop equivalent
    fun <T> foldRange(
        start: Int,
        end: Int,
        initial: T,
        op: (T, Int) -> T
    ): T {
        return (start until end).fold(initial, op)
    }

    // Functional map over range
    fun mapRange(
        start: Int,
        end: Int,
        transform: (Int) -> Double
    ): List<Double> {
        return (start until end).map(transform)
    }
}