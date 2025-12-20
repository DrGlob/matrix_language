package org.example

import advanced.MatMulBenchmark
import cli.FileRunner
import org.example.core.*
import org.example.dsl.matrixComputation
import org.example.utils.printInfo
import org.example.dsl.ControlFlow
import org.example.extensions.hadamardProduct
import org.example.utils.toFormattedString


fun main(args: Array<String>) {
    when {
        args.size == 1 && args[0] == "--benchmark-matmul" -> MatMulBenchmark.run()
        args.size > 1 -> {
            println("Usage: matrix-dsl [--benchmark-matmul|script]")
            kotlin.system.exitProcess(64)
        }
        args.size == 1 -> {
            FileRunner.runFile(args[0])
        }
        else -> {
            FileRunner.runPrompt()
        }
    }
}

//fun main() {
//    val result = matrixComputation {
//        declare("A", matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0)))
//        declare("B", matrixOf(listOf(5.0, 6.0), listOf(7.0, 8.0)))
//
//        get("A").printInfo("Matrix A")
//        get("B").printInfo("Matrix B")
//
//        val conditionalResult = ifThen(
//            { get("A").numRows > 1 },
//            {
//                println("Condition true: adding A + B")
//                get("A") + get("B")
//            },
//            {
//                println("Condition false: multiplying A * B")
//                get("A") * get("B")
//            }
//        )
//
//        conditionalResult.printInfo("Conditional result")
//
//        // Создание вектора через fold
//        val vector = Matrix(listOf(
//            foldRange(0, 2, emptyList<Double>()) { acc, i ->
//                acc + listOf(i * 1.0)
//            }
//        ))
//
//        vector.printInfo("Vector")
//
//        // Финальный результат
//        conditionalResult * vector.transpose()
//    }
//
//    println("\n=== Final Result ===")
//    println(result.toFormattedString())
//
//    // Пример дополнительных операций
//    val m1 = matrixOf(listOf(1.0, 2.0), listOf(3.0, 4.0))
//    val m2 = matrixOf(listOf(2.0, 0.0), listOf(0.0, 2.0))
//
//    println("\n=== Hadamard Product Example ===")
//    println("m1 hadamard m2:")
//    println(m1.hadamardProduct(m2))
//}
