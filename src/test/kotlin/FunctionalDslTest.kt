import org.example.parser.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FunctionalDslTest {
    private fun evalLast(source: String): Value {
        val lexer = Lexer(source)
        val parser = Parser(lexer.scanTokens())
        val statements = parser.parse()

        val interpreter = Interpreter()
        var last: Value = UnitValue

        statements.forEachIndexed { idx, stmt ->
            val isLastExpr = idx == statements.lastIndex && stmt is Stmt.Expression
            if (isLastExpr) {
                last = interpreter.evaluator.eval(stmt.expression, interpreter.environment)
            } else {
                interpreter.execute(stmt)
            }
        }
        return last
    }

    @Test
    fun `if expression chooses correct branch`() {
        val result = evalLast(
            """
            let a = 1;
            let b = 2;
            let x = if a < b then 10 else 20;
            x;
            """.trimIndent()
        )
        assertEquals(10.0, (result as NumberValue).value)
    }

    @Test
    fun `let-in creates lexical scope`() {
        val result = evalLast(
            """
            let y = let x = 1 in x + 2;
            y;
            """.trimIndent()
        )
        assertEquals(3.0, (result as NumberValue).value)
    }

    @Test
    fun `map doubles matrix elements`() {
        val result = evalLast(
            """
            let arr = [1, 2, 3];
            let doubled = arr.map { it * 2 };
            doubled;
            """.trimIndent()
        )
        val matrix = (result as MatrixValue).matrix
        val flat = (0 until matrix.numRows).flatMap { r ->
            (0 until matrix.numCols).map { c -> matrix[r, c] }
        }
        assertEquals(listOf(2.0, 4.0, 6.0), flat)
    }

    @Test
    fun `reduce sums matrix elements`() {
        val result = evalLast(
            """
            let arr = [1, 2, 3, 4];
            let s = arr.reduce(0) { acc, x -> acc + x };
            s;
            """.trimIndent()
        )
        assertEquals(10.0, (result as NumberValue).value)
    }

    @Test
    fun `polynomial via reduce over coefficients`() {
        val result = evalLast(
            """
            let coeff = [2, 3, 1];
            let x = 2;
            let value = coeff.reduce(0) { acc, c -> acc * x + c };
            value;
            """.trimIndent()
        )
        assertEquals(15.0, (result as NumberValue).value)
    }
}
