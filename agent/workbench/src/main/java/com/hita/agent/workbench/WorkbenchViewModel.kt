package com.hita.agent.workbench

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.data.agent.AgentBackendApi
import com.hita.agent.core.data.localrag.LocalRagRepository
import com.hita.agent.core.domain.agent.PlannerConfig
import com.hita.agent.core.domain.repo.EasRepository
import android.net.Uri
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WorkbenchViewModel(
    private val api: AgentBackendApi,
    private val easRepository: EasRepository?,
    private val plannerConfig: PlannerConfig,
    private val localRagRepository: LocalRagRepository?
) : ViewModel() {
    private val _state = MutableStateFlow(WorkbenchUiState())
    val state: StateFlow<WorkbenchUiState> = _state.asStateFlow()
    private val loop = AgenticLoop(api, easRepository, plannerConfig, localRagRepository)

    fun updateInput(value: String) {
        _state.value = _state.value.copy(input = value)
    }

    fun sendQuery() {
        val query = _state.value.input.trim()
        if (query.isBlank()) return
        if (query.startsWith("/mcp")) {
            DebugLog.d("WorkbenchVM", "mcp command len=${query.length}")
            handleMcpCommand(query)
            return
        }
        DebugLog.d("WorkbenchVM", "sendQuery len=${query.length}")
        appendMessage(WorkbenchMessage(WorkbenchRole.USER, query))
        _state.value = _state.value.copy(input = "", busy = true, error = null)
        viewModelScope.launch {
            val trace = defaultTrace("workbench")
            val events = runCatching { loop.run(query, trace) }
                .getOrElse { ex ->
                    listOf(AgenticEvent.Observation("error: ${ex.message ?: "agent loop failed"}"))
                }
            events.forEach { event ->
                when (event) {
                    is AgenticEvent.Action -> appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, event.text))
                    is AgenticEvent.Observation -> appendMessage(WorkbenchMessage(WorkbenchRole.ASSISTANT, event.text))
                }
            }
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun onToolsClicked() {
        DebugLog.d("WorkbenchVM", "tools clicked")
        handleMcpCommand("/mcp list")
    }

    fun actionNotImplemented(label: String) {
        appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, "$label not implemented"))
    }

    fun onUploadSelected(uri: Uri?) {
        if (uri == null) {
            appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, "Upload canceled"))
            return
        }
        if (localRagRepository == null) {
            appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, "Local RAG unavailable"))
            return
        }
        DebugLog.d("WorkbenchVM", "index file uri=${uri.scheme}")
        _state.value = _state.value.copy(busy = true, error = null)
        viewModelScope.launch {
            val result = runCatching { localRagRepository.indexFile(uri) }
            val message = result.fold(
                onSuccess = { indexed ->
                    val name = indexed.displayName ?: indexed.uri
                    val mime = indexed.mimeType ?: "unknown"
                    val size = indexed.sizeBytes?.toString() ?: "unknown"
                    DebugLog.d("WorkbenchVM", "index ok name=$name status=${indexed.status}")
                    "Indexed: $name (mime=$mime, size=$size, status=${indexed.status.name.lowercase()})\\nuri: ${indexed.uri}"
                },
                onFailure = { ex -> "Index failed: ${ex.message ?: "unknown"}" }
            )
            appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, message))
            _state.value = _state.value.copy(busy = false)
        }
    }

    fun onDownloadTarget(uriString: String?) {
        if (uriString.isNullOrBlank()) {
            appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, "Download canceled"))
            return
        }
        DebugLog.d("WorkbenchVM", "download target selected")
        appendMessage(WorkbenchMessage(WorkbenchRole.TOOL, "Selected download target: $uriString"))
    }

    private fun defaultTrace(tag: String): JsonObject {
        val client = buildJsonObject {
            put("app", JsonPrimitive("HITA_Angent"))
            put("version", JsonPrimitive("0.1.0"))
            put("platform", JsonPrimitive("android"))
        }
        val tags = buildJsonArray { add(JsonPrimitive(tag)) }
        return buildJsonObject {
            put("trace_id", JsonPrimitive(UUID.randomUUID().toString()))
            put("request_id", JsonPrimitive(UUID.randomUUID().toString()))
            put("loggable", JsonPrimitive(false))
            put("client", client)
            put("tags", tags)
        }
    }

    private fun appendMessage(message: WorkbenchMessage) {
        val updated = _state.value.messages + message
        _state.value = _state.value.copy(messages = updated)
    }

    private fun handleMcpCommand(raw: String) {
        appendMessage(WorkbenchMessage(WorkbenchRole.USER, raw))
        _state.value = _state.value.copy(input = "", busy = true, error = null)
        viewModelScope.launch {
            val trace = defaultTrace("mcp")
            val result = runCatching { dispatchMcp(raw, trace) }
                .getOrElse { ex -> "error: ${ex.message ?: "mcp failed"}" }
            appendMessage(WorkbenchMessage(WorkbenchRole.ASSISTANT, result))
            _state.value = _state.value.copy(busy = false)
        }
    }

    private fun dispatchMcp(raw: String, trace: JsonObject): String {
        val trimmed = raw.trim()
        val tokens = trimmed.split(" ", limit = 3)
        if (tokens.size < 2) return "usage: /mcp list | /mcp call <server> <tool> <json>"
        return when (tokens[1]) {
            "list", "tools" -> {
                DebugLog.d("WorkbenchVM", "mcp list tools")
                val response = api.invokeSkill("mcp.list_tools", JsonObject(emptyMap()), trace)
                if (!response.ok) return formatError(response.error?.message ?: "mcp.list_tools failed")
                val output = response.output ?: JsonNull
                formatMcpTools(output)
            }
            "call" -> {
                val match = Regex("^/mcp\\s+call\\s+(\\S+)\\s+(\\S+)(\\s+(.+))?$")
                    .find(trimmed)
                if (match == null) return "usage: /mcp call <server> <tool> <json>"
                val server = match.groupValues[1]
                val tool = match.groupValues[2]
                DebugLog.d("WorkbenchVM", "mcp call server=$server tool=$tool")
                val argsJson = match.groupValues.getOrNull(4).orEmpty().ifBlank { "{}" }
                val argsElement = runCatching { api.json.parseToJsonElement(argsJson) }.getOrNull() ?: JsonNull
                val arguments = argsElement as? JsonObject ?: JsonObject(emptyMap())
                val input = buildJsonObject {
                    put("server", JsonPrimitive(server))
                    put("tool", JsonPrimitive(tool))
                    put("arguments", arguments)
                }
                val response = api.invokeSkill("mcp.call_tool", input, trace)
                if (!response.ok) return formatError(response.error?.message ?: "mcp.call_tool failed")
                val output = response.output ?: JsonNull
                formatMcpCall(output)
            }
            else -> "unsupported /mcp command: ${tokens[1]}"
        }
    }

    private fun formatMcpTools(output: JsonElement): String {
        val obj = output as? JsonObject ?: return "tools: ${output.toString().take(200)}"
        val toolsByServer = obj["tools"]?.jsonObject ?: return "tools: ${output.toString().take(200)}"
        val builder = StringBuilder()
        toolsByServer.forEach { (server, tools) ->
            builder.append("$server:\n")
            val list = tools as? kotlinx.serialization.json.JsonArray
            if (list == null) {
                builder.append("  - ${tools.toString().take(200)}\n")
                return@forEach
            }
            list.forEach { tool ->
                val name = tool.jsonObject["name"]?.jsonPrimitive?.content ?: "unknown"
                val desc = tool.jsonObject["description"]?.jsonPrimitive?.content ?: ""
                builder.append("  - $name")
                if (desc.isNotBlank()) builder.append(": $desc")
                builder.append("\n")
            }
        }
        return builder.toString().trimEnd()
    }

    private fun formatMcpCall(output: JsonElement): String {
        val obj = output as? JsonObject ?: return "result: ${output.toString().take(200)}"
        val result = obj["result"]
        return if (result == null) {
            "result: ${output.toString().take(200)}"
        } else {
            "result: ${result.toString().take(400)}"
        }
    }

    private fun formatError(message: String): String {
        return "error: $message"
    }

    companion object {
        fun factory(
            api: AgentBackendApi,
            easRepository: EasRepository?,
            plannerConfig: PlannerConfig,
            localRagRepository: LocalRagRepository?
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return WorkbenchViewModel(api, easRepository, plannerConfig, localRagRepository) as T
                }
            }
    }
}

enum class WorkbenchRole {
    USER,
    ASSISTANT,
    TOOL
}

data class WorkbenchMessage(
    val role: WorkbenchRole,
    val content: String
)

data class WorkbenchUiState(
    val messages: List<WorkbenchMessage> = emptyList(),
    val input: String = "",
    val busy: Boolean = false,
    val error: String? = null
)
