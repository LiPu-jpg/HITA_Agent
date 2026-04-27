package com.limpu.hitax.agent.llm

import android.app.Application
import com.limpu.hitax.utils.LogUtils
import com.limpu.hitax.utils.AppConstants
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.agent.core.AgentTraceEvent
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.agent.tools.ReActToolInput
import com.limpu.hitax.agent.tools.ReActToolRegistry
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object LlmChatService {

    private val toolRegistry = ReActToolRegistry.createDefault()

    suspend fun chat(
        history: List<ChatMessage>,
        timetableId: String?,
        application: Application,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
        onResult: (LlmChatResult) -> Unit,
    ) {
        withContext(Dispatchers.IO) {
            try {
                val userMessage = history.lastOrNull { it.role == "user" }?.content ?: ""

                onTrace(AgentTraceEvent(stage = "react_start", message = "分析中…", payload = ""))

                val contextMessage = ReactPromptBuilder.build(
                    context = application,
                    userMessage = userMessage,
                    history = history,
                )

                // BLOCKED: Backend ReAct is intentionally disabled.
                // Reason: Backend cannot execute Android-local tools (timetable query, add activity).
                // Also, cloud ReAct causes concurrency issues with multi-user shared state.
                // Use localReAct() below instead.
                // val result = AgentBackendClient.aiReactSync(contextMessage)
                // if (result == null) { onResult(...); return@withContext }
                val (answer, thinking) = localReAct(
                    contextMessage = contextMessage,
                    userMessage = userMessage,
                    history = history,
                    application = application,
                    timetableId = timetableId,
                    agentProvider = agentProvider,
                    onTrace = onTrace,
                ) ?: return@withContext onResult(LlmChatResult.Error(error = "本地 AI 推理失败"))

                LogUtils.d( "[DEBUG] localReAct returned answer length=${answer.length}, thinking length=${thinking.length}")

                LogUtils.d( "[DEBUG] Calling onResult with Success, answer length=${answer.length}")
                onResult(LlmChatResult.Success(text = answer, thinking = thinking))
                LogUtils.d( "[DEBUG] onResult called successfully")
            } catch (e: Exception) {
                LogUtils.e( "Chat failed", e)
                onResult(LlmChatResult.Error(error = e.message ?: "调用失败"))
            }
        }
    }

    private suspend fun localReAct(
        contextMessage: String,
        userMessage: String,
        history: List<ChatMessage>,
        application: Application,
        timetableId: String?,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
    ): Pair<String, String>? {
        val messages = mutableListOf(
            ChatMessage(role = "system", content = contextMessage),
            ChatMessage(role = "user", content = userMessage),
        )
        val thinkingSteps = mutableListOf<String>()

        repeat(15) { step ->
            LogUtils.d( "[DEBUG] localReAct step ${step + 1}/10 starting, messages count=${messages.size}")
            onTrace(AgentTraceEvent(stage = "react_step", message = "步骤 ${step + 1}/15: 正在思考…", payload = ""))

            val request = ChatCompletionRequest(
                model = LlmClient.MODEL,
                messages = messages,
            )
            LogUtils.d( "[DEBUG] Sending MiniMax request with ${messages.size} messages")
            var response: retrofit2.Response<ChatCompletionResponse>? = null
            var retryCount = 0
            val maxRetries = 5
            while (retryCount < maxRetries) {
                val currentResponse = try {
                    LlmClient.service.chatCompletion(LlmClient.authHeader(), request).execute()
                } catch (e: Exception) {
                    LogUtils.e( "MiniMax API error in step ${step + 1}, attempt ${retryCount + 1}: ${e.javaClass.simpleName}: ${e.message}", e)
                    return null
                }
                response = currentResponse
                if (currentResponse.isSuccessful) break
                val code = currentResponse.code()
                if (code == 529 || code == 503 || code == 429) {
                    retryCount++
                    val delayMs = 3000L * retryCount
                    LogUtils.w( "MiniMax HTTP $code in step ${step + 1}, attempt $retryCount/$maxRetries, retrying after ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    break
                }
            }
            val finalResponse = response ?: return null

            LogUtils.d( "[DEBUG] MiniMax response received: isSuccessful=${finalResponse.isSuccessful}, code=${finalResponse.code()}")

            if (!finalResponse.isSuccessful) {
                val errorBody = finalResponse.errorBody()?.string()
                LogUtils.e( "MiniMax HTTP ${finalResponse.code()} in step ${step + 1}: $errorBody")
                return null
            }

            val body = finalResponse.body()
            LogUtils.d( "[DEBUG] MiniMax body=${body != null}, choices=${body?.choices?.size}")
            if (body == null) {
                LogUtils.e( "[DEBUG] MiniMax body is null in step ${step + 1}")
                return null
            }
            val choice = body.choices?.firstOrNull()
            if (choice == null) {
                LogUtils.e( "[DEBUG] MiniMax choices empty in step ${step + 1}")
                return null
            }
            val content = choice.message?.content
            if (content == null) {
                LogUtils.e( "[DEBUG] MiniMax content null in step ${step + 1}, message=${choice.message}")
                return null
            }
            val finishReason = choice.finishReason

            LogUtils.d( "[DEBUG] MiniMax raw content length=${content.length} finish_reason=$finishReason")
            LogUtils.d( "[DEBUG] MiniMax raw content:\n$content")

            val parsed = parseLocalReActStep(content)
            LogUtils.d( "[DEBUG] Parsed thought length=${parsed.thought.length} action=${parsed.action} actionInput length=${parsed.actionInput.length}")
            LogUtils.d( "[DEBUG] Parsed thought:\n${parsed.thought}")

            thinkingSteps.add("步骤 ${step + 1}: ${parsed.thought.take(100)}${if (parsed.action.isNotBlank()) " → ${parsed.action}" else ""}")

            onTrace(AgentTraceEvent(
                stage = "react_step",
                message = "思考: ${parsed.thought.take(80)}${if (parsed.action.isNotBlank()) " → ${parsed.action}" else ""}",
                payload = parsed.actionInput.take(200),
            ))

            if (parsed.action == "答案" || parsed.action.isBlank()) {
                LogUtils.d( "[DEBUG] Returning final answer length=${parsed.thought.length}")
                val thinking = thinkingSteps.joinToString("\n")
                return Pair(parsed.thought, thinking)
            }

            LogUtils.d( "[DEBUG] Executing tool action=${parsed.action} input=${parsed.actionInput}")
            val observation = toolRegistry.get(parsed.action)
                ?.execute(ReActToolInput(
                    actionInput = parsed.actionInput,
                    userMessage = userMessage,
                    application = application,
                    timetableId = timetableId,
                    agentProvider = agentProvider,
                    onTrace = onTrace,
                ))
                ?: "未知工具: ${parsed.action}"
            LogUtils.d( "[DEBUG] Tool observation length=${observation?.length ?: 0}")

            onTrace(AgentTraceEvent(stage = "react_step", message = "观察: ${observation.take(100)}", payload = observation.take(500)))

            messages.add(ChatMessage(role = "assistant", content = content))
            messages.add(ChatMessage(role = "user", content = "观察结果: $observation"))
            LogUtils.d( "[DEBUG] Appended observation to messages, now ${messages.size} messages")
        }

        val thinking = thinkingSteps.joinToString("\n")
        return Pair("达到最大步骤限制（15步），请简化您的问题", thinking)
    }

    internal data class ParsedStep(val thought: String, val action: String, val actionInput: String)

    private fun parseLocalReActStep(text: String): ParsedStep {
        val cleanedText = text
            .replace(Regex("""<think>.*?</think>""", RegexOption.DOT_MATCHES_ALL), "")
            .trim()

        val thoughtRegex = Regex("(?is)思考[：:]\\s*(.+?)(?=\\n动作[：:]|\\n答案[：:]|$)")
        val actionRegex = Regex("(?i)动作[：:]\\s*(\\S+)")
        val actionInputRegex = Regex("(?is)动作输入[：:]\\s*(.+?)(?=\\n思考[：:]|\\n观察[：:]|$)")
        val answerRegex = Regex("(?is)答案[：:]\\s*(.+)")

        val thought = thoughtRegex.find(cleanedText)?.groupValues?.get(1)?.trim() ?: ""
        val action = actionRegex.find(cleanedText)?.groupValues?.get(1)?.trim() ?: ""
        val actionInput = actionInputRegex.find(cleanedText)?.groupValues?.get(1)?.trim() ?: ""

        if (action.isBlank() && thought.isNotBlank()) {
            val answerMatch = answerRegex.find(cleanedText)
            if (answerMatch != null) {
                return ParsedStep(thought = answerMatch.groupValues[1].trim(), action = "答案", actionInput = "")
            }
        }

        if (thought.isBlank() && action.isBlank()) {
            LogUtils.d( "[DEBUG] No ReAct tags found, treating as direct answer")
            return ParsedStep(thought = cleanedText.trim(), action = "答案", actionInput = "")
        }

        return ParsedStep(thought = thought, action = action, actionInput = actionInput)
    }
}
private suspend fun localLlmGenerate(prompt: String): String? {
    val request = ChatCompletionRequest(
        model = LlmClient.MODEL,
        messages = listOf(ChatMessage(role = "user", content = prompt)),
    )
    return try {
        val response = LlmClient.service.chatCompletion(LlmClient.authHeader(), request).execute()
        if (!response.isSuccessful) {
            LogUtils.e("localLlmGenerate HTTP ${response.code()}")
            return null
        }
        response.body()?.choices?.firstOrNull()?.message?.content?.trim()
    } catch (e: Exception) {
        LogUtils.e("localLlmGenerate error: ${e.message}", e)
        null
    }
}

sealed class LlmChatResult {
    data class Success(val text: String, val thinking: String? = null) : LlmChatResult()
    data class Error(val error: String) : LlmChatResult()
}

/**
 * 处理带附件的消息，使用智谱多模态API理解附件内容
 * 仅用于图片和视频理解，文档类文件优先本地处理
 * 然后将理解结果添加到对话历史，继续使用 miniMAX 进行对话
 */
suspend fun LlmChatService.chatWithAttachment(
    history: List<ChatMessage>,
    attachmentBase64: String,
    attachmentMimeType: String,
    timetableId: String?,
    application: Application,
    agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
    onTrace: (AgentTraceEvent) -> Unit,
    onResult: (LlmChatResult) -> Unit,
) {
    withContext(Dispatchers.IO) {
        try {
            onTrace(AgentTraceEvent(stage = "react_start", message = "正在理解附件内容…", payload = ""))

            // 1. 判断附件类型并处理
            val contentType = when {
                attachmentMimeType.startsWith("image/") -> "image_url"
                attachmentMimeType.startsWith("video/") -> "video_url"
                else -> {
                    LogUtils.e("Unsupported attachment type: $attachmentMimeType")
                    return@withContext onResult(LlmChatResult.Error("不支持的附件类型：$attachmentMimeType\n\n支持的类型：\n- 图片：JPG、PNG、GIF、WebP\n- 视频：MP4、MOV\n\n注意：PDF、Word、Excel、PowerPoint 请直接上传，系统会自动本地解析"))
                }
            }

            val userMessage = history.lastOrNull { it.role == "user" }?.content ?: ""
            // 提取纯文本问题（移除附件标记）
            val textQuestion = userMessage.lines().filterNot {
                it.startsWith("[") || it.startsWith("[附件") || it.startsWith("[文件")
            }.joinToString("\n").trim()

            val attachmentContent: ZhipuContent = when {
                contentType == "image_url" -> {
                    val imageType = when {
                        attachmentMimeType.contains("jpeg") || attachmentMimeType.contains("jpg") -> "image/jpeg"
                        attachmentMimeType.contains("png") -> "image/png"
                        attachmentMimeType.contains("gif") -> "image/gif"
                        attachmentMimeType.contains("webp") -> "image/webp"
                        else -> "image/jpeg"
                    }
                    ZhipuContent(
                        type = "image_url",
                        image_url = ZhipuImageUrl("data:$imageType;base64,$attachmentBase64")
                    )
                }
                contentType == "video_url" -> {
                    ZhipuContent(
                        type = "video_url",
                        video_url = ZhipuVideoUrl("data:$attachmentMimeType;base64,$attachmentBase64")
                    )
                }
                else -> {
                    // 不应该到这里
                    ZhipuContent(
                        type = "text",
                        text = "[错误：不支持的文件类型]"
                    )
                }
            }

            val zhipuMessages = listOf(
                ZhipuMessage(
                    role = "user",
                    content = listOf(
                        attachmentContent,
                        ZhipuContent(
                            type = "text",
                            text = when (contentType) {
                                "image_url" -> "请详细描述这张图片的内容，包括主要物体、场景、文字、颜色等细节。"
                                "video_url" -> "请详细描述这个视频的主要内容，包括场景、人物、动作、关键信息等。"
                                "file_upload" -> "请详细分析这个PDF文档的内容，包括：\n1. 文档的主要主题和目的\n2. 关键信息和要点\n3. 重要的数据、公式或结论\n4. 文档的结构和章节\n5. 如果是作业或试卷，请列出题目内容"
                                else -> "请描述这个文件的内容。"
                            }
                        )
                    )
                )
            )

            val zhipuRequest = ZhipuChatRequest(
                model = when (contentType) {
                    "file_upload" -> "glm-4-flash"  // PDF 使用支持文档的模型
                    else -> "glm-4.6v-flash"  // 图片/视频使用视觉模型
                },
                messages = zhipuMessages,
                stream = false
            )

            val attachmentDescription = try {
                val response = ZhipuClient.service.chatCompletion(
                    ZhipuClient.authHeader(),
                    zhipuRequest
                ).execute()

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.string()
                    LogUtils.e("Zhipu API error: ${response.code()} - $errorBody")
                    return@withContext onResult(LlmChatResult.Error("附件理解失败：HTTP ${response.code()}"))
                }

                val body = response.body()
                if (body == null) {
                    LogUtils.e("Zhipu response body is null")
                    return@withContext onResult(LlmChatResult.Error("附件理解失败：响应为空"))
                }

                body.choices.firstOrNull()?.message?.content ?: "无法理解附件内容"
            } catch (e: Exception) {
            LogUtils.e("chatWithAttachment error: ${e.message}", e)
                return@withContext onResult(LlmChatResult.Error("附件理解失败：${e.message}"))
            }

            // 2. 将附件理解结果添加到对话历史
            val enhancedUserMessage = "$textQuestion\n\n[附件内容理解]\n$attachmentDescription"

            val newHistory = history.toMutableList()
            // 替换最后一条用户消息
            if (newHistory.isNotEmpty() && newHistory.last().role == "user") {
                newHistory[newHistory.size - 1] = ChatMessage(role = "user", content = enhancedUserMessage)
            }

            // 3. 使用 miniMAX 继续对话
            LlmChatService.chat(
                history = newHistory,
                timetableId = timetableId,
                application = application,
                agentProvider = agentProvider,
                onTrace = onTrace,
                onResult = onResult
            )

        } catch (e: Exception) {
            LogUtils.e("chatWithAttachment error: ${e.message}", e)
            onResult(LlmChatResult.Error("处理失败：${e.message}"))
        }
    }
}
