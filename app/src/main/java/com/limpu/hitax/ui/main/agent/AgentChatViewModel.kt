package com.limpu.hitax.ui.main.agent

import android.app.Application
import android.net.Uri
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.agent.llm.ChatMessage
import com.limpu.hitax.agent.llm.LlmChatResult
import com.limpu.hitax.agent.llm.LlmChatService
import com.limpu.hitax.agent.llm.chatWithAttachment
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.AppDatabase
import com.limpu.hitax.data.model.chat.ChatMessageEntity
import com.limpu.hitax.data.model.chat.ChatSession
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject

internal fun nextSessionIdAfterDeletion(
    deletedSessionId: String,
    currentSessionId: String?,
    remainingLatestSessionId: String?,
): String? {
    return if (deletedSessionId == currentSessionId) remainingLatestSessionId else currentSessionId
}

@HiltViewModel
class AgentChatViewModel @Inject constructor(
    private val application: Application,
) : ViewModel() {

    private val db = AppDatabase.getDatabase(application)
    private val sessionDao = db.chatSessionDao()
    private val messageDao = db.chatMessageDao()
    private val ioExecutor = Executors.newSingleThreadExecutor()

    private val _messages = MutableLiveData<List<AgentChatMessage>>(emptyList())
    val messages: LiveData<List<AgentChatMessage>> = _messages

    private val _status = MutableLiveData("")
    val status: LiveData<String> = _status

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _pendingAttachment = MutableLiveData<Uri?>(null)
    val pendingAttachment: LiveData<Uri?> = _pendingAttachment

    val sessions = sessionDao.getAll()

    @Volatile
    private var messageList: List<AgentChatMessage> = emptyList()

    private val chatHistory = mutableListOf<ChatMessage>()

    @Volatile
    var currentSessionId: String? = null
        private set

    private var placeholderMessage: AgentChatMessage? = null

    fun addMessage(message: AgentChatMessage) {
        synchronized(this) {
            messageList = messageList + message
            _messages.postValue(messageList)
        }
        val sid = currentSessionId ?: return
        ioExecutor.execute {
            messageDao.save(
                ChatMessageEntity(
                    sessionId = sid,
                    role = message.role.name,
                    text = message.text,
                    timestampMs = message.timestampMs,
                )
            )
            sessionDao.updateTitle(sid, deriveTitle(), System.currentTimeMillis())
        }
    }

    fun updateOrCreatePlaceholder(text: String) {
        synchronized(this) {
            val existing = placeholderMessage
            if (existing != null) {
                val idx = messageList.indexOf(existing)
                if (idx >= 0) {
                    val updated = existing.copy(text = text)
                    messageList = messageList.toMutableList().apply { set(idx, updated) }
                    placeholderMessage = updated
                    _messages.postValue(messageList)
                    return
                }
            }
            val newPlaceholder = AgentChatMessage(
                role = AgentChatMessage.Role.ASSISTANT,
                text = text,
                isPlaceholder = true,
            )
            placeholderMessage = newPlaceholder
            messageList = messageList + newPlaceholder
            _messages.postValue(messageList)
        }
    }

    fun replacePlaceholder(finalMessage: AgentChatMessage) {
        synchronized(this) {
            val existing = placeholderMessage ?: return
            val idx = messageList.indexOf(existing)
            if (idx >= 0) {
                messageList = messageList.toMutableList().apply { set(idx, finalMessage) }
            } else {
                messageList = messageList + finalMessage
            }
            placeholderMessage = null
            _messages.postValue(messageList)
        }
        val sid = currentSessionId ?: return
        ioExecutor.execute {
            messageDao.save(
                ChatMessageEntity(
                    sessionId = sid,
                    role = finalMessage.role.name,
                    text = finalMessage.text,
                    timestampMs = finalMessage.timestampMs,
                )
            )
            sessionDao.updateTitle(sid, deriveTitle(), System.currentTimeMillis())
        }
    }

    private fun deriveTitle(): String {
        val firstUser = messageList.firstOrNull { it.role == AgentChatMessage.Role.USER }
        return firstUser?.text?.take(20)?.plus(if ((firstUser.text.length) > 20) "…" else "")
            ?: "新对话"
    }

    fun setStatus(text: String) {
        _status.postValue(text)
    }

    fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }

    fun setPendingAttachment(uri: Uri?) {
        _pendingAttachment.value = uri
    }

    fun clearPendingAttachment() {
        _pendingAttachment.value = null
    }

    fun createNewSession() {
        val session = ChatSession()
        currentSessionId = session.id
        chatHistory.clear()
        messageList = emptyList()
        _messages.postValue(emptyList())
        _status.postValue("")
        ioExecutor.execute { sessionDao.save(session) }
    }

    fun switchToSession(sessionId: String) {
        if (sessionId == currentSessionId) return
        currentSessionId = sessionId
        chatHistory.clear()
        ioExecutor.execute {
            val entities = messageDao.getBySessionSync(sessionId)
            val restored = entities.map { e ->
                AgentChatMessage(
                    role = AgentChatMessage.Role.valueOf(e.role),
                    text = e.text,
                    timestampMs = e.timestampMs,
                )
            }
            synchronized(this) {
                messageList = restored
                chatHistory.addAll(
                    restored.filter {
                        it.role == AgentChatMessage.Role.USER || it.role == AgentChatMessage.Role.ASSISTANT
                    }.map {
                        ChatMessage(
                            role = if (it.role == AgentChatMessage.Role.USER) "user" else "assistant",
                            content = it.text,
                        )
                    }
                )
            }
            _messages.postValue(restored)
        }
    }

    fun deleteSession(session: ChatSession) {
        ioExecutor.execute {
            messageDao.deleteBySession(session.id)
            sessionDao.delete(session)
            val nextSessionId = nextSessionIdAfterDeletion(
                deletedSessionId = session.id,
                currentSessionId = currentSessionId,
                remainingLatestSessionId = sessionDao.getLatest()?.id,
            )
            when {
                nextSessionId == null -> createNewSession()
                nextSessionId != currentSessionId -> switchToSession(nextSessionId)
            }
        }
    }

    fun ensureSession() {
        if (currentSessionId != null) return
        ioExecutor.execute {
            val latest = sessionDao.getLatest()
            if (latest != null) {
                switchToSession(latest.id)
            } else {
                createNewSession()
            }
        }
    }

    fun sendToLlm(
        text: String,
        agentProvider: com.limpu.hitax.agent.core.AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    ) {
        ensureSession()
        synchronized(this) {
            chatHistory.add(ChatMessage(role = "user", content = text))
        }

        viewModelScope.launch {
            setLoading(true)

            LlmChatService.chat(
                history = synchronized(this) { chatHistory.toList() },
                timetableId = null,
                application = application,
                agentProvider = agentProvider,
                onTrace = { trace ->
                    val statusText = when (trace.stage) {
                        "react_start" -> "正在分析您的问题…"
                        "react_step" -> {
                            val action = trace.message.substringAfter("→ ", "").trim()
                            when {
                                action.contains("get_timetable") -> "正在查询课表…"
                                action.contains("search_course") -> "正在搜索课程信息…"
                                action.contains("get_course_detail") -> "正在获取课程详情…"
                                action.contains("search_teacher") -> "正在搜索教师信息…"
                                action.contains("web_search") -> "正在搜索网页…"
                                action.contains("brave_answer") -> "正在搜索答案…"
                                action.contains("rag_search") -> "正在搜索知识库…"
                                action.contains("crawl_page") -> "正在爬取网页…"
                                action.contains("crawl_site") -> "正在爬取网站…"
                                action.contains("submit_review") -> "正在提交评价…"
                                action.contains("add_activity") -> "正在添加活动…"
                                else -> "正在思考…"
                            }
                        }
                        else -> "正在处理…"
                    }
                    updateOrCreatePlaceholder(statusText)
                },
                onResult = { result ->
                    when (result) {
                        is LlmChatResult.Success -> {
                            synchronized(this) {
                                chatHistory.add(ChatMessage(role = "assistant", content = result.text))
                            }
                            replacePlaceholder(AgentChatMessage(
                                role = AgentChatMessage.Role.ASSISTANT,
                                text = result.text,
                                thinking = result.thinking,
                            ))
                            setStatus("完成")
                        }
                        is LlmChatResult.Error -> {
                            replacePlaceholder(AgentChatMessage(
                                role = AgentChatMessage.Role.ASSISTANT,
                                text = "操作失败: ${result.error}",
                            ))
                            setStatus("失败")
                        }
                    }
                    setLoading(false)
                },
            )
        }
    }

    fun sendToLlmWithAttachment(
        text: String,
        fileName: String,
        base64Content: String,
        mimeType: String,
        agentProvider: com.limpu.hitax.agent.core.AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    ) {
        ensureSession()

        val userMessage = "$text\n\n[文件: $fileName]"

        synchronized(this) {
            chatHistory.add(ChatMessage(role = "user", content = userMessage))
        }

        viewModelScope.launch {
            setLoading(true)

            LlmChatService.chatWithAttachment(
                history = synchronized(this) { chatHistory.toList() },
                attachmentBase64 = base64Content,
                attachmentMimeType = mimeType,
                timetableId = null,
                application = application,
                agentProvider = agentProvider,
                onTrace = { trace: AgentTraceEvent ->
                    val statusText = when (trace.stage) {
                        "react_start" -> "正在分析附件…"
                        "react_step" -> {
                            val action = trace.message.substringAfter("→ ", "").trim()
                            when {
                                action.contains("get_timetable") -> "正在查询课表…"
                                action.contains("search_course") -> "正在搜索课程信息…"
                                action.contains("get_course_detail") -> "正在获取课程详情…"
                                action.contains("search_teacher") -> "正在搜索教师信息…"
                                action.contains("web_search") -> "正在搜索网页…"
                                action.contains("brave_answer") -> "正在搜索答案…"
                                action.contains("rag_search") -> "正在搜索知识库…"
                                else -> "正在思考…"
                            }
                        }
                        else -> "正在处理…"
                    }
                    updateOrCreatePlaceholder(statusText)
                },
                onResult = { result: LlmChatResult ->
                    when (result) {
                        is LlmChatResult.Success -> {
                            synchronized(this) {
                                // chatWithAttachment 已经在内部更新了历史，这里不需要再次添加
                                // 但需要确保最新的回复在历史中
                                if (!chatHistory.any { it.role == "assistant" && it.content == result.text }) {
                                    chatHistory.add(ChatMessage(role = "assistant", content = result.text))
                                }
                            }
                            replacePlaceholder(AgentChatMessage(
                                role = AgentChatMessage.Role.ASSISTANT,
                                text = result.text,
                                thinking = result.thinking
                            ))
                        }
                        is LlmChatResult.Error -> {
                            replacePlaceholder(AgentChatMessage(
                                role = AgentChatMessage.Role.ASSISTANT,
                                text = "抱歉，处理过程中出现错误：${result.error}"
                            ))
                        }
                    }
                    setLoading(false)
                },
            )
        }
    }
}
