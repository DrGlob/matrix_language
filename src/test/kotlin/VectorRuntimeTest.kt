import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.example.core.Matrix
import org.example.core.Vector
import org.example.parser.Interpreter
import org.example.parser.ListValue
import org.example.parser.MatrixValue
import org.example.parser.NativeFunctionValue
import org.example.parser.NumberValue
import org.example.parser.PairValue
import org.example.parser.VectorValue

class VectorRuntimeTest {
    private val evaluator = Interpreter().evaluator

    @Test
    fun `vector map doubles elements`() {
        val vector = VectorValue(Vector.fromList(listOf(1.0, 2.0, 3.0)))
        val doubleFn = NativeFunctionValue(1) { _, args ->
            NumberValue((args[0] as NumberValue).value * 2)
        }

        val mapped = evaluator.applyMap(vector, doubleFn) as VectorValue

        assertEquals(listOf(2.0, 4.0, 6.0), mapped.vector.toList())
    }

    @Test
    fun `vector reduce sums values`() {
        val vector = VectorValue(Vector.fromList(listOf(1.5, 2.5, 3.0)))
        val sumFn = NativeFunctionValue(2) { _, args ->
            NumberValue((args[0] as NumberValue).value + (args[1] as NumberValue).value)
        }

        val reduced = evaluator.applyReduce(vector, NumberValue(0.0), sumFn) as NumberValue

        assertEquals(7.0, reduced.value, 1e-9)
    }

    @Test
    fun `zip and unzip roundtrip vectors`() {
        val left = VectorValue(Vector.fromList(listOf(1.0, 2.0, 3.0)))
        val right = VectorValue(Vector.fromList(listOf(10.0, 20.0, 30.0)))

        val zipped = evaluator.applyZip(left, right) as ListValue
        assertEquals(3, zipped.items.size)
        val firstPair = zipped.items.first() as PairValue
        assertTrue(firstPair.first is NumberValue && firstPair.second is NumberValue)

        val unzipped = evaluator.applyUnzip(zipped) as PairValue
        val leftOut = (unzipped.first as VectorValue).vector.toList()
        val rightOut = (unzipped.second as VectorValue).vector.toList()

        assertEquals(listOf(1.0, 2.0, 3.0), leftOut)
        assertEquals(listOf(10.0, 20.0, 30.0), rightOut)
    }

    @Test
    fun `list and matrix convert to vector`() {
        val listVector = evaluator.expectVector("vector", ListValue(listOf(NumberValue(1.0), NumberValue(2.0))))
        assertEquals(listOf(1.0, 2.0), listVector.toList())

        val matrix = Matrix.fromInitializer(1, 3) { _, c -> (c + 1).toDouble() }
        val matrixVector = evaluator.expectVector("vector", MatrixValue(matrix))
        assertEquals(listOf(1.0, 2.0, 3.0), matrixVector.toList())
    }
}
