package com.stupidtree.hitax.ui.main.agent

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import com.stupidtree.hitax.agent.llm.ChatMessage
import com.stupidtree.hitax.agent.llm.LlmChatResult
import com.stupidtree.hitax.agent.llm.LlmChatService
import com.stupidtree.hitax.agent.timetable.TimetableAgentInput
import com.stupidtree.hitax.agent.timetable.TimetableAgentOutput
import com.stupidtree.hitax.data.AppDatabase
import com.stupidtree.hitax.data.model.chat.ChatMessageEntity
import com.stupidtree.hitax.data.model.chat.ChatSession
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

internal fun nextSessionIdAfterDeletion(
    deletedSessionId: String,
    currentSessionId: String?,
    remainingLatestSessionId: String?,
): String? {
    return if (deletedSessionId == currentSessionId) remainingLatestSessionId else currentSessionId
}

class AgentChatViewModel(application: Application) : AndroidViewModel(application) {

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
        agentProvider: com.stupidtree.hitax.agent.core.AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    ) {
        ensureSession()
        synchronized(this) {
            chatHistory.add(ChatMessage(role = "user", content = text))
        }

        viewModelScope.launch {
            setLoading(true)
            setStatus("正在思考…")

            LlmChatService.chat(
                history = synchronized(this) { chatHistory.toList() },
                timetableId = null,
                application = getApplication(),
                agentProvider = agentProvider,
                onTrace = { trace ->
                    addMessage(
                        AgentChatMessage(
                            role = AgentChatMessage.Role.TRACE,
                            text = "[${trace.stage}] ${trace.message}"
                                    + if (trace.payload.isNotBlank()) "\n${trace.payload}" else "",
                        )
                    )
                },
                onResult = { result ->
                    when (result) {
                        is LlmChatResult.Success -> {
                            synchronized(this) {
                                chatHistory.add(ChatMessage(role = "assistant", content = result.text))
                            }
                            addMessage(AgentChatMessage(role = AgentChatMessage.Role.ASSISTANT, text = result.text))
                            setStatus("完成")
                        }
                        is LlmChatResult.Error -> {
                            addMessage(AgentChatMessage(role = AgentChatMessage.Role.ASSISTANT, text = "操作失败: ${result.error}"))
                            setStatus("失败")
                        }
                    }
                    setLoading(false)
                },
            )
        }
    }
}
