package org.example.parser

import org.example.core.MatrixFactory
import kotlin.math.sqrt

object StdLib {
    fun install(base: Environment, evaluator: Evaluator): Environment {
        var env = base

        fun add(name: String, arity: Int, impl: (Evaluator, List<Value>) -> Value) {
            env = env.define(name, NativeFunctionValue(arity, impl))
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
