package planner

import org.example.parser.Environment
import org.example.parser.Evaluator
import org.example.parser.Expr
import org.example.parser.MatrixLangRuntimeException
import org.example.parser.Value

/**
 * Событие исполнения узла для внешних слушателей/логов.
 */
data class AstExecutionEvent(
    val nodeId: AstExecNodeId,
    val status: AstNodeStatus,
    val timestampMillis: Long,
    val error: Throwable? = null
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
        val errText = event.error?.let { " error=${it.message}" } ?: ""
        println("[exec] node=${event.nodeId.value} status=${event.status} ts=${event.timestampMillis}$errText")
    }
}

/**
 * Адаптер: listener поверх sink.
 */
class SinkBackedListener(private val sink: AstExecutionSink) : AstExecutionListener {
    override fun onEvent(event: AstExecutionEvent) = sink.record(event)
}

/**
 * Настройки пайплайна исполнения AST.
 */
data class AstExecutionConfig(
    val corePoolSize: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1),
    val maxPoolSize: Int = Runtime.getRuntime().availableProcessors().coerceAtLeast(1) * 2,
    val keepAliveMillis: Long = 1_000L,
    val queueCapacity: Int = 128,
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

        val dependencies = mutableMapOf<AstExecNodeId, MutableList<AstExecNodeId>>()
        val children = mutableMapOf<AstExecNodeId, MutableList<AstExecNodeId>>()

        for (node in ordered) {
            val nodeId = AstExecNodeId(node.id)
            if (!dependencies.containsKey(nodeId)) dependencies[nodeId] = mutableListOf()
            if (!children.containsKey(nodeId)) children[nodeId] = mutableListOf()

            node.inputs.forEach { input ->
                val inputId = AstExecNodeId(input.id)
                dependencies.getOrPut(nodeId) { mutableListOf() }.add(inputId)
                children.getOrPut(inputId) { mutableListOf() }.add(nodeId)
            }
        }

        return ordered.map { node ->
            val nodeId = AstExecNodeId(node.id)
            AstExecNode(
                id = nodeId,
                dependencies = dependencies[nodeId].orEmpty(),
                children = children[nodeId].orEmpty(),
                task = { null }
            )
        }
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
        val runtimeStates = mutableMapOf<AstExecNodeId, AstNodeRuntimeState>()

        fun emit(nodeId: AstExecNodeId, status: AstNodeStatus, error: Throwable? = null) {
            runtimeStates[nodeId] = AstNodeRuntimeState(nodeId, status, error)
            eventListener?.onEvent(
                AstExecutionEvent(
                    nodeId = nodeId,
                    status = status,
                    timestampMillis = System.currentTimeMillis(),
                    error = error
                )
            )
        }

        nodes.forEach { emit(it.id, AstNodeStatus.PENDING) }
        rootNode?.let { emit(it.id, AstNodeStatus.RUNNING) }

        return try {
            val result = evaluator.eval(rootExpr, env)
            nodes.forEach { emit(it.id, AstNodeStatus.SUCCESS) }
            result
        } catch (e: MatrixLangRuntimeException) {
            rootNode?.let { emit(it.id, AstNodeStatus.ERROR, e) }
            throw e
        } catch (e: Exception) {
            rootNode?.let { emit(it.id, AstNodeStatus.ERROR, e) }
            throw e
        }
    }
}
