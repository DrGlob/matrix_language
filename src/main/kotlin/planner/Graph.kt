package planner

import org.example.parser.Expr
import org.example.parser.TokenType

private object Ids {
    private var counter = 0
    fun next(prefix: String) = "${prefix}_${counter++}"
}

/**
 * Узел графа вычислений для планировщика.
 *
 * Представляет высокоуровневые операции над AST и хранит оценку стоимости.
 */
sealed class GraphNode(
    open val id: String,
    open val inputs: List<GraphNode>,
    open val costEstimate: Double,
    open val meta: NodeMeta = NodeMeta()
) {
    data class ValueNode(
        override val id: String,
        val label: String,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, emptyList(), costEstimate = 1.0, meta = meta)

    data class AddNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(10.0), meta = meta)

    data class MulNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(
        id,
        inputs,
        costEstimate = meta.mulCost(),
        meta = meta
    )

    data class ScaleNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(5.0), meta = meta)

    data class MapNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(100.0), meta = meta)

    data class ReduceNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(100.0), meta = meta)

    data class ZipNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(50.0), meta = meta)

    data class UnzipNode(
        override val id: String,
        override val inputs: List<GraphNode>,
        override val meta: NodeMeta = NodeMeta()
    ) : GraphNode(id, inputs, costEstimate = meta.defaultCost(50.0), meta = meta)
}

/**
 * Метаданные узла (размеры/длины) для оценки стоимости.
 */
data class NodeMeta(
    val rows: Int? = null,
    val cols: Int? = null,
    val length: Int? = null
) {
    fun mulCost(): Double {
        val r = rows ?: return defaultCost(1_000.0)
        val c = cols ?: return defaultCost(1_000.0)
        val inner = cols ?: return defaultCost(1_000.0)
        return r * c * inner.toDouble()
    }

    fun defaultCost(fallback: Double): Double = when {
        length != null -> length.toDouble()
        rows != null && cols != null -> (rows * cols).toDouble()
        else -> fallback
    }
}

/**
 * Результат планирования: корень графа, все узлы и предупреждения.
 */
data class Plan(
    val root: GraphNode,
    val nodes: Set<GraphNode>,
    val warnings: List<String>
)

/**
 * Строит граф вычислений из AST.
 */
class Planner {
    private val warnings = mutableListOf<String>()
    private val nodes = mutableSetOf<GraphNode>()

    fun plan(expr: Expr): Plan {
        val root = build(expr) ?: GraphNode.ValueNode(Ids.next("val"), "unit")
        return Plan(root, nodes, warnings)
    }

    private fun register(node: GraphNode): GraphNode {
        nodes.add(node)
        return node
    }

    private fun build(expr: Expr): GraphNode? {
        return when (expr) {
            is Expr.NumberLiteral -> register(GraphNode.ValueNode(Ids.next("num"), expr.value.toString()))
            is Expr.StringLiteral -> register(GraphNode.ValueNode(Ids.next("str"), expr.value))
            is Expr.MatrixLiteral -> register(
                GraphNode.ValueNode(
                    Ids.next("matrix"),
                    "matrix",
                    NodeMeta(rows = expr.rows.size, cols = expr.rows.firstOrNull()?.size)
                )
            )
            is Expr.Variable -> register(GraphNode.ValueNode(Ids.next("var"), expr.name))
            is Expr.Binary -> buildBinary(expr)
            is Expr.Unary -> build(expr.right)
            is Expr.IfExpr -> {
                val thenNode = build(expr.thenBranch)
                val elseNode = build(expr.elseBranch)
                register(
                    GraphNode.AddNode(
                        Ids.next("if"),
                        listOfNotNull(thenNode, elseNode)
                    )
                )
            }
            is Expr.LetInExpr -> build(expr.bodyExpr)
            is Expr.CallExpr -> buildCall(expr)
            is Expr.MethodCallExpr -> buildMethod(expr)
            is Expr.PropertyAccess -> build(expr.receiver)
            is Expr.Function, is Expr.LambdaExpr -> register(GraphNode.ValueNode(Ids.next("fn"), "lambda"))
            else -> null
        }
    }

    private fun buildBinary(expr: Expr.Binary): GraphNode? {
        val left = build(expr.left)
        val right = build(expr.right)
        return when (expr.operator.type) {
            TokenType.PLUS -> register(GraphNode.AddNode(Ids.next("add"), listOfNotNull(left, right)))
            TokenType.MINUS -> register(GraphNode.AddNode(Ids.next("sub"), listOfNotNull(left, right)))
            TokenType.MULTIPLY -> {
                val scale = expr.left is Expr.NumberLiteral || expr.right is Expr.NumberLiteral
                if (scale) register(GraphNode.ScaleNode(Ids.next("scale"), listOfNotNull(left ?: right)))
                else register(GraphNode.MulNode(Ids.next("mul"), listOfNotNull(left, right)))
            }
            else -> left
        }
    }

    private fun buildCall(expr: Expr.CallExpr): GraphNode? {
        val callee = expr.callee
        val name = if (callee is Expr.Variable) callee.name else null
        return when (name) {
            "map" -> {
                val target = expr.args.getOrNull(0)?.let { build(it) }
                register(GraphNode.MapNode(Ids.next("map"), listOfNotNull(target)))
            }
            "reduce" -> {
                val target = expr.args.getOrNull(0)?.let { build(it) }
                register(GraphNode.ReduceNode(Ids.next("reduce"), listOfNotNull(target)))
            }
            "zip" -> register(
                GraphNode.ZipNode(
                    Ids.next("zip"),
                    expr.args.mapNotNull { build(it) }
                )
            )
            "unzip" -> register(
                GraphNode.UnzipNode(
                    Ids.next("unzip"),
                    expr.args.mapNotNull { build(it) }
                )
            )
            else -> expr.args.lastOrNull()?.let { build(it) }
        }
    }

    private fun buildMethod(expr: Expr.MethodCallExpr): GraphNode? {
        val receiver = build(expr.receiver)
        return when (expr.method) {
            "map" -> register(GraphNode.MapNode(Ids.next("map"), listOfNotNull(receiver)))
            "reduce" -> register(GraphNode.ReduceNode(Ids.next("reduce"), listOfNotNull(receiver)))
            "zip" -> {
                val other = expr.args.getOrNull(0)?.let { build(it) }
                register(GraphNode.ZipNode(Ids.next("zip"), listOfNotNull(receiver, other)))
            }
            "unzip" -> register(GraphNode.UnzipNode(Ids.next("unzip"), listOfNotNull(receiver)))
            else -> receiver
        }
    }
}
