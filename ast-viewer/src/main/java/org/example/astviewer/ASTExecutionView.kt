package org.example.astviewer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.AttributeSet
import android.view.View
import kotlin.math.max

class ASTExecutionView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var nodes: List<AstExecNodeDto> = emptyList()
        set(value) {
            field = value
            computeLayout()
            invalidate()
        }

    var states: Map<String, AstNodeRuntimeState> = emptyMap()
        set(value) {
            field = value
            invalidate()
        }

    private val nodePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    private val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = dp(1.5f)
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = sp(10f)
        textAlign = Paint.Align.CENTER
    }

    private val positions = mutableMapOf<String, PointF>()
    private var levelRows: List<List<AstExecNodeDto>> = emptyList()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        computeLayout()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (nodes.isEmpty()) return

        drawEdges(canvas)
        drawNodes(canvas)
    }

    private fun drawEdges(canvas: Canvas) {
        for (node in nodes) {
            val end = positions[node.id] ?: continue
            for (depId in node.deps) {
                val start = positions[depId] ?: continue
                canvas.drawLine(start.x, start.y, end.x, end.y, edgePaint)
            }
        }
    }

    private fun drawNodes(canvas: Canvas) {
        val radius = nodeRadius()
        val labelOffset = radius + textPaint.textSize

        for (node in nodes) {
            val center = positions[node.id] ?: continue
            val state = states[node.id] ?: AstNodeRuntimeState.PENDING
            nodePaint.color = colorForState(state)
            canvas.drawCircle(center.x, center.y, radius, nodePaint)
            canvas.drawCircle(center.x, center.y, radius, nodeStrokePaint)

            val label = "${node.id}:${node.op}"
            canvas.drawText(label, center.x, center.y + labelOffset, textPaint)
        }
    }

    private fun computeLayout() {
        positions.clear()
        if (nodes.isEmpty() || width == 0 || height == 0) return

        levelRows = buildLevels(nodes)

        val radius = nodeRadius()
        val labelGap = radius + textPaint.textSize
        val availableHeight = height - paddingTop - paddingBottom
        val minVertical = radius * 3f + textPaint.textSize
        val verticalSpacing = max(minVertical, availableHeight / max(1, levelRows.size).toFloat())
        val startY = paddingTop.toFloat() + radius + textPaint.textSize

        for ((level, row) in levelRows.withIndex()) {
            if (row.isEmpty()) continue
            val y = startY + level * verticalSpacing
            val availableWidth = width - paddingLeft - paddingRight
            val horizontalSpacing = availableWidth / (row.size + 1).toFloat()
            for ((index, node) in row.withIndex()) {
                val x = paddingLeft + (index + 1) * horizontalSpacing
                positions[node.id] = PointF(x, y)
            }
        }
    }

    private fun buildLevels(nodes: List<AstExecNodeDto>): List<List<AstExecNodeDto>> {
        val byId = nodes.associateBy { it.id }
        val indegree = mutableMapOf<String, Int>()
        val dependents = mutableMapOf<String, MutableList<String>>()

        nodes.forEach { indegree[it.id] = 0 }
        nodes.forEach { node ->
            node.deps.forEach { depId ->
                if (byId.containsKey(depId)) {
                    indegree[node.id] = (indegree[node.id] ?: 0) + 1
                    dependents.getOrPut(depId) { mutableListOf() }.add(node.id)
                }
            }
        }

        val queue = ArrayDeque<String>()
        indegree.filter { it.value == 0 }.keys.forEach { queue.add(it) }

        val levelMap = mutableMapOf<String, Int>()
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val level = levelMap[id] ?: 0
            val nextList = dependents[id] ?: emptyList()
            for (childId in nextList) {
                val nextLevel = max(levelMap[childId] ?: 0, level + 1)
                levelMap[childId] = nextLevel
                val newDegree = (indegree[childId] ?: 1) - 1
                indegree[childId] = newDegree
                if (newDegree == 0) {
                    queue.add(childId)
                }
            }
        }

        nodes.forEach { if (!levelMap.containsKey(it.id)) levelMap[it.id] = 0 }
        val maxLevel = levelMap.values.maxOrNull() ?: 0
        val levels = MutableList(maxLevel + 1) { mutableListOf<AstExecNodeDto>() }
        nodes.forEach { node -> levels[levelMap[node.id] ?: 0].add(node) }
        return levels
    }

    private fun nodeRadius(): Float = dp(16f)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun sp(value: Float): Float = value * resources.displayMetrics.scaledDensity

    private fun colorForState(state: AstNodeRuntimeState): Int {
        return when (state) {
            AstNodeRuntimeState.PENDING -> Color.GRAY
            AstNodeRuntimeState.RUNNING -> Color.BLUE
            AstNodeRuntimeState.SUCCESS -> Color.GREEN
            AstNodeRuntimeState.ERROR -> Color.RED
            AstNodeRuntimeState.CANCELLED -> Color.parseColor("#FFA500")
            AstNodeRuntimeState.SKIPPED -> Color.LTGRAY
        }
    }
}
