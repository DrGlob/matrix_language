package advanced

import kotlin.random.Random
import kotlin.system.measureTimeMillis
import org.example.core.MatMulConfig
import org.example.core.MatMulDefaults
import org.example.core.Matrix
import org.example.core.MultiplicationAlgorithm
import org.example.core.multiply

object MatMulBenchmark {
    private val sizes = listOf(64, 128, 256, 512, 1024)
    private val algorithms = listOf(
        MultiplicationAlgorithm.SEQUENTIAL,
        MultiplicationAlgorithm.PARALLEL,
        MultiplicationAlgorithm.STRASSEN
    )

    @JvmStatic
    fun main(args: Array<String>) {
        run()
    }

    fun run() {
        val random = Random(42)
        val results = mutableListOf<ResultRow>()

        println("Benchmarking matrix multiplication (sizes=${sizes.joinToString()}, algorithms=${algorithms.joinToString()})")

        for (size in sizes) {
            val a = randomMatrix(size, size, random)
            val b = randomMatrix(size, size, random)

            warmup(a, b)

            for (algorithm in algorithms) {
                val config = configFor(algorithm)
                val duration = bestOfMillis(3) { multiply(a, b, config) }
                results += ResultRow(size = size, algorithm = algorithm, timeMs = duration)
            }
        }

        printTable(results)
    }

    private fun configFor(algorithm: MultiplicationAlgorithm): MatMulConfig =
        MatMulDefaults.default().copy(algorithm = algorithm)

    private fun warmup(a: Matrix, b: Matrix) {
        multiply(a, b, MatMulDefaults.default())
    }

    private fun bestOfMillis(runs: Int, block: () -> Unit): Long {
        var best = Long.MAX_VALUE
        repeat(runs) {
            val elapsed = measureTimeMillis { block() }
            if (elapsed < best) best = elapsed
        }
        return best
    }

    private fun randomMatrix(rows: Int, cols: Int, random: Random): Matrix =
        Matrix.fromInitializer(rows, cols) { _, _ -> random.nextDouble(-1.0, 1.0) }

    private fun printTable(results: List<ResultRow>) {
        println("\nsize | algorithm  | timeMs")
        println("-----+------------+--------")
        results.forEach { row ->
            println("%4d | %-10s | %6d".format(row.size, row.algorithm, row.timeMs))
        }
    }

    private data class ResultRow(val size: Int, val algorithm: MultiplicationAlgorithm, val timeMs: Long)
}
