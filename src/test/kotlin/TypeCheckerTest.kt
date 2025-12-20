import org.example.parser.Expr
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Token
import org.example.parser.TokenType
import org.example.type.FunctionType
import org.example.type.ListType
import org.example.type.NumberType
import org.example.type.PairType
import org.example.type.StringType
import org.example.type.TypeEnv
import org.example.type.inferType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TypeCheckerTest {
    private fun plusToken() = Token(TokenType.PLUS, "+", null, 0, 0)

    @Test
    fun `map infers list result type`() {
        val env = TypeEnv().extend("xs", ListType(NumberType))
        val expr = Expr.CallExpr(
            Expr.Variable("map"),
            listOf(
                Expr.Variable("xs"),
                Expr.LambdaExpr(listOf("x"), Expr.Variable("x"))
            )
        )

        val result = inferType(expr, env)
        assertEquals(ListType(NumberType), result)
    }

    @Test
    fun `reduce infers accumulator type`() {
        val env = TypeEnv().extend("xs", ListType(NumberType))
        val expr = Expr.CallExpr(
            Expr.Variable("reduce"),
            listOf(
                Expr.Variable("xs"),
                Expr.NumberLiteral(0.0),
                Expr.LambdaExpr(
                    listOf("acc", "x"),
                    Expr.Binary(Expr.Variable("acc"), plusToken(), Expr.Variable("x"))
                )
            )
        )

        val result = inferType(expr, env)
        assertEquals(NumberType, result)
    }

    @Test
    fun `zip and unzip infer pair list types`() {
        val env = TypeEnv()
            .extend("xs", ListType(NumberType))
            .extend("ys", ListType(StringType))
            .extend("pairs", ListType(PairType(NumberType, StringType)))

        val zipExpr = Expr.CallExpr(
            Expr.Variable("zip"),
            listOf(Expr.Variable("xs"), Expr.Variable("ys"))
        )
        val unzipExpr = Expr.CallExpr(
            Expr.Variable("unzip"),
            listOf(Expr.Variable("pairs"))
        )

        assertEquals(
            ListType(PairType(NumberType, StringType)),
            inferType(zipExpr, env)
        )
        assertEquals(
            PairType(ListType(NumberType), ListType(StringType)),
            inferType(unzipExpr, env)
        )
    }

    @Test
    fun `map rejects function with wrong param type`() {
        val env = TypeEnv()
            .extend("xs", ListType(NumberType))
            .extend("f", FunctionType(listOf(StringType), StringType))

        val expr = Expr.CallExpr(
            Expr.Variable("map"),
            listOf(Expr.Variable("xs"), Expr.Variable("f"))
        )

        val error = assertFailsWith<MatrixLangRuntimeException> {
            inferType(expr, env)
        }
        assertTrue(error.message!!.contains("expected Number"))
        assertTrue(error.message!!.contains("got String"))
    }
}
