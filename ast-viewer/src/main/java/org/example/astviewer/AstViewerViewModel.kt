package org.example.astviewer

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream

enum class AstNodeRuntimeState {
    PENDING,
    RUNNING,
    SUCCESS,
    ERROR,
    CANCELLED,
    SKIPPED
}

data class AstExecNodeDto(
    val id: String,
    val op: String,
    val deps: List<String>
)

data class AstNodeEventDto(
    val nodeId: String,
    val status: AstNodeRuntimeState,
    val timestampMillis: Long
)

class AstViewerViewModel : ViewModel() {
    private val graphLiveData = MutableLiveData<List<AstExecNodeDto>>(emptyList())
    private val nodeStatesLiveData = MutableLiveData<Map<String, AstNodeRuntimeState>>(emptyMap())

    val graph: LiveData<List<AstExecNodeDto>> = graphLiveData
    val nodeStates: LiveData<Map<String, AstNodeRuntimeState>> = nodeStatesLiveData

    fun loadGraphAndEvents(input: InputStream) {
        viewModelScope.launch(Dispatchers.IO) {
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val trimmed = line.trim()
                    if (trimmed.isEmpty()) return@forEach

                    val json = JSONObject(trimmed)
                    when (json.optString("type")) {
                        "graph" -> {
                            val nodes = parseGraph(json)
                            graphLiveData.postValue(nodes)
                            val initialStates = nodes.associate { it.id to AstNodeRuntimeState.PENDING }
                            nodeStatesLiveData.postValue(initialStates)
                        }
                        "event" -> {
                            val event = parseEvent(json)
                            val current = nodeStatesLiveData.value?.toMutableMap() ?: mutableMapOf()
                            current[event.nodeId] = event.status
                            nodeStatesLiveData.postValue(current)
                        }
                        else -> Unit
                    }
                }
            }
        }
    }

    private fun parseGraph(json: JSONObject): List<AstExecNodeDto> {
        val nodes = mutableListOf<AstExecNodeDto>()
        val nodesArray = json.optJSONArray("nodes") ?: JSONArray()
        for (i in 0 until nodesArray.length()) {
            val node = nodesArray.getJSONObject(i)
            val id = node.optString("id")
            val op = node.optString("op")
            val depsArray = node.optJSONArray("deps") ?: JSONArray()
            val deps = MutableList(depsArray.length()) { idx -> depsArray.optString(idx) }
            nodes.add(AstExecNodeDto(id = id, op = op, deps = deps))
        }
        return nodes
    }

    private fun parseEvent(json: JSONObject): AstNodeEventDto {
        val nodeId = json.optString("nodeId")
        val status = parseStatus(json.optString("status"))
        val ts = json.optLong("timestampMillis")
        return AstNodeEventDto(nodeId = nodeId, status = status, timestampMillis = ts)
    }

    private fun parseStatus(value: String): AstNodeRuntimeState {
        return when (value) {
            "PENDING" -> AstNodeRuntimeState.PENDING
            "RUNNING" -> AstNodeRuntimeState.RUNNING
            "SUCCESS", "COMPLETED" -> AstNodeRuntimeState.SUCCESS
            "ERROR", "FAILED" -> AstNodeRuntimeState.ERROR
            "CANCELLED" -> AstNodeRuntimeState.CANCELLED
            "SKIPPED" -> AstNodeRuntimeState.SKIPPED
            else -> AstNodeRuntimeState.PENDING
        }
    }
}
