package advanced

import org.example.core.Matrix
import org.example.core.toBlocks

/**
 * Небольшой прогон для проверки плоского хранения и блочного представления.
 */
fun main() {
    val base = Matrix.fromInitializer(5, 4) { r, c -> (r * 10 + c).toDouble() }

    val blocks = base.toBlocks(blockSize = 2)
    println("Block grid: ${blocks.blockRows}x${blocks.blockCols}")
    println("Last block size: ${blocks.blocks.last().last().rows}x${blocks.blocks.last().last().cols}")

    val rebuilt = blocks.toMatrix()
    check(rebuilt == base) { "Block reconstruction mismatch" }

    val a = Matrix.identity(3)
    val b = Matrix.fromInitializer(3, 3) { r, c -> (r + c + 1).toDouble() }

    println("\nA + B:")
    println(a + b)

    println("\nA * B:")
    println(a * b)

    println("\nSmoke check passed")
}
