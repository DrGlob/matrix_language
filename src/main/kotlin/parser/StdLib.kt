package org.example.parser

import org.example.core.MatMulDefaults
import org.example.core.MatrixFactory
import org.example.core.MultiplicationAlgorithm
import kotlin.math.sqrt

object StdLib {
    fun install(base: Environment, evaluator: Evaluator): Environment {
        var env = base

        fun add(name: String, arity: Int, impl: (Evaluator, List<Value>) -> Value) {
            env = env.define(name, NativeFunctionValue(arity, impl))
        }

        fun coefficients(name: String, value: Value, ev: Evaluator): List<Double> = when (value) {
            is ListValue -> value.items.mapIndexed { idx, v -> ev.expectNumber("$name coefficient[$idx]", v) }
            is MatrixValue -> {
                val matrix = value.matrix
                if (matrix.numRows != 1 && matrix.numCols != 1) {
                    throw runtimeError("$name expects coefficients as 1D list or vector")
                }
                val values = DoubleArray(matrix.numRows * matrix.numCols)
                for (i in values.indices) {
                    val r = i / matrix.numCols
                    val c = i % matrix.numCols
                    values[i] = matrix[r, c]
                }
                values.toList()
            }
            else -> throw runtimeError("$name expects coefficients as a list or vector")
        }

        fun algorithm(name: String, value: Value): MultiplicationAlgorithm {
            val algoName = when (value) {
                is StringValue -> value.value.lowercase()
                else -> throw runtimeError("$name expects algorithm name as string")
            }
            return when (algoName) {
                "sequential", "seq" -> MultiplicationAlgorithm.SEQUENTIAL
                "parallel", "par", "async" -> MultiplicationAlgorithm.PARALLEL
                "strassen", "fast" -> MultiplicationAlgorithm.STRASSEN
                else -> throw runtimeError("Unknown algorithm '$algoName' for $name")
            }
        }

        add("map", 2) { ev, args ->
            val fn = ev.expectCallable("map", args[1])
            ev.applyMap(args[0], fn)
        }

        add("reduce", 3) { ev, args ->
            val fn = ev.expectCallable("reduce", args[2])
            ev.applyReduce(args[0], args[1], fn)
        }

        add("zip", 2) { ev, args -> ev.applyZip(args[0], args[1]) }
        add("unzip", 1) { ev, args -> ev.applyUnzip(args[0]) }

        add("filter", 2) { ev, args ->
            val predicate = ev.expectCallable("filter", args[1])
            ev.applyFilter(args[0], predicate)
        }

        add("compose", 2) { ev, args ->
            val f = ev.expectCallable("compose", args[0])
            val g = ev.expectCallable("compose", args[1])
            NativeFunctionValue(g.arity()) { innerEv, callArgs ->
                val intermediate = g.call(innerEv, callArgs)
                f.call(innerEv, listOf(intermediate))
            }
        }

        add("zeros", 2) { ev, args ->
            val rows = ev.expectNumber("zeros", args[0]).toInt()
            val cols = ev.expectNumber("zeros", args[1]).toInt()
            MatrixValue(MatrixFactory.zeros(rows, cols))
        }

        add("ones", 2) { ev, args ->
            val rows = ev.expectNumber("ones", args[0]).toInt()
            val cols = ev.expectNumber("ones", args[1]).toInt()
            MatrixValue(MatrixFactory.ones(rows, cols))
        }

        add("identity", 1) { ev, args ->
            val size = ev.expectNumber("identity", args[0]).toInt()
            MatrixValue(MatrixFactory.identity(size))
        }

        add("poly", 2) { ev, args ->
            val matrix = ev.expectMatrix("poly", args[0])
            val coeffs = coefficients("poly", args[1], ev)
            MatrixValue(matrix.polyEval(coeffs))
        }

        add("polyWith", 3) { ev, args ->
            val matrix = ev.expectMatrix("polyWith", args[0])
            val coeffs = coefficients("polyWith", args[1], ev)
            val algo = algorithm("polyWith", args[2])
            val config = MatMulDefaults.default().copy(algorithm = algo)
            MatrixValue(matrix.polyEval(coeffs, config = config))
        }

        add("transpose", 1) { ev, args ->
            MatrixValue(ev.expectMatrix("transpose", args[0]).transpose())
        }

        add("rows", 1) { ev, args ->
            NumberValue(ev.expectMatrix("rows", args[0]).numRows.toDouble())
        }

        add("cols", 1) { ev, args ->
            NumberValue(ev.expectMatrix("cols", args[0]).numCols.toDouble())
        }

        add("norm", 1) { ev, args ->
            val matrix = ev.expectMatrix("norm", args[0])
            var sum = 0.0
            for (r in 0 until matrix.numRows) {
                for (c in 0 until matrix.numCols) {
                    val v = matrix[r, c]
                    sum += v * v
                }
            }
            NumberValue(sqrt(sum))
        }

        return env
    }
}
