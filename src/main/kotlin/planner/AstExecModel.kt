package planner

/**
 * Идентификатор узла исполнения AST/DAG.
 */
data class AstExecNodeId(val value: String)

/**
 * Описание узла исполнения с зависимостями и задачей вычисления.
 */
data class AstExecNode(
    val id: AstExecNodeId,
    val dependencies: List<AstExecNodeId>, // от кого зависит
    val children: List<AstExecNodeId>,     // кому он нужен
    val task: () -> Any?                   // вычисление узла
)

/**
 * Статус выполнения узла.
 */
enum class AstNodeStatus {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELLED,
    SKIPPED
}

/**
 * Текущее состояние узла во время исполнения.
 */
data class AstNodeRuntimeState(
    val id: AstExecNodeId,
    val status: AstNodeStatus,
    val error: Throwable? = null
)
