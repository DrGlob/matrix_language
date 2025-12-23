package planner

import org.example.parser.Environment
import org.example.parser.Evaluator
import org.example.parser.Expr
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Value

/**
 * Состояние узла исполнения AST.
 */
enum class AstNodeStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED
}

/**
 * Событие исполнения узла для внешних слушателей/логов.
 */
data class AstExecutionEvent(
    val nodeId: String,
    val status: AstNodeStatus,
    val timestampMillis: Long
)

/**
 * Принимает события исполнения.
 */
interface AstExecutionListener {
    fun onEvent(event: AstExecutionEvent)
}

/**
 * Sink для записи событий (например, лог или буфер).
 */
interface AstExecutionSink {
    fun record(event: AstExecutionEvent)
}

/**
 * Пустой sink по умолчанию.
 */
object NoopExecutionSink : AstExecutionSink {
    override fun record(event: AstExecutionEvent) = Unit
}

/**
 * Sink, который пишет события в stdout.
 */
class LoggingExecutionSink : AstExecutionSink {
    override fun record(event: AstExecutionEvent) {
        println("[exec] node=${event.nodeId} status=${event.status} ts=${event.timestampMillis}")
    }
}

/**
 * Адаптер: listener поверх sink.
 */
class SinkBackedListener(private val sink: AstExecutionSink) : AstExecutionListener {
    override fun onEvent(event: AstExecutionEvent) = sink.record(event)
}

/**
 * Узел исполнения, соответствующий узлу графа планировщика.
 */
data class AstExecNode(
    val id: String,
    val graphNode: GraphNode,
    var status: AstNodeStatus = AstNodeStatus.PENDING
)

/**
 * Настройки пайплайна исполнения AST.
 */
data class AstExecutionConfig(
    val enableTypeCheck: Boolean = true,
    val enablePlanning: Boolean = true
)

/**
 * Преобразует граф планировщика в упорядоченный список узлов исполнения.
 */
object AstExecMapper {
    fun fromPlannerGraph(plan: Plan): List<AstExecNode> {
        val ordered = mutableListOf<GraphNode>()
        val visited = mutableSetOf<String>()

        fun visit(node: GraphNode) {
            if (!visited.add(node.id)) return
            node.inputs.forEach { visit(it) }
            ordered.add(node)
        }

        visit(plan.root)
        return ordered.map { AstExecNode(it.id, it) }
    }
}

/**
 * Исполнитель AST-выражения с опциональным логированием узлов графа.
 *
 * Граф используется как план (метаданные), а вычисление производится
 * через [Evaluator] по исходному [Expr].
 */
class AstExecutionEngine(private val evaluator: Evaluator) {
    fun execute(
        nodes: List<AstExecNode>,
        rootExpr: Expr,
        env: Environment,
        listener: AstExecutionListener? = null,
        sink: AstExecutionSink? = null
    ): Value {
        val eventListener = listener ?: sink?.let { SinkBackedListener(it) }
        val rootNode = nodes.lastOrNull()

        fun emit(node: AstExecNode, status: AstNodeStatus) {
            node.status = status
            eventListener?.onEvent(
                AstExecutionEvent(
                    nodeId = node.id,
                    status = status,
                    timestampMillis = System.currentTimeMillis()
                )
            )
        }

        if (rootNode != null) {
            emit(rootNode, AstNodeStatus.RUNNING)
        }

        return try {
            val result = evaluator.eval(rootExpr, env)
            nodes.forEach { emit(it, AstNodeStatus.COMPLETED) }
            result
        } catch (e: MatrixLangRuntimeException) {
            if (rootNode != null) {
                emit(rootNode, AstNodeStatus.FAILED)
            }
            throw e
        } catch (e: Exception) {
            if (rootNode != null) {
                emit(rootNode, AstNodeStatus.FAILED)
            }
            throw e
        }
    }
}
