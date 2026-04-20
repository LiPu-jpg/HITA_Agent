package com.stupidtree.hitax.agent.llm

import android.app.Application
import android.util.Log
import com.stupidtree.hitax.agent.core.AgentProvider
import com.stupidtree.hitax.agent.core.AgentTraceEvent
import com.stupidtree.hitax.agent.remote.AgentBackendClient
import com.stupidtree.hitax.agent.remote.PrServerClient
import com.stupidtree.hitax.agent.timetable.ArrangementInput
import com.stupidtree.hitax.agent.timetable.TimetableAgentInput
import com.stupidtree.hitax.agent.timetable.TimetableAgentOutput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object LlmChatService {

    private const val TAG = "LlmChatService"

    private val addActivityRegex = Regex(
        """\{"local_action"\s*:\s*"add_activity"\s*,\s*"name"\s*:\s*"([^"]+)"\s*,\s*"from"\s*:\s*"?([^",]+)"?\s*,\s*"to"\s*:\s*"?([^",]+)"?\s*(?:,\s*"place"\s*:\s*"([^"]*)")?\s*\}"""
    )

    private fun parseTimestampOrIso(raw: String): Long? {
        val trimmed = raw.trim().removeSurrounding("\"")
        trimmed.toLongOrNull()?.let { return it }
        val isoFormats = listOf(
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.CHINA),
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        )
        for (fmt in isoFormats) {
            try {
                return fmt.parse(trimmed)?.time
            } catch (_: Exception) {}
        }
        return null
    }

    private fun parseDayOffset(text: String): Int? {
        val cal = Calendar.getInstance()
        val todayDow = cal.get(Calendar.DAY_OF_WEEK)
        val dowMap = mapOf(
            "周一" to Calendar.MONDAY, "星期一" to Calendar.MONDAY,
            "周二" to Calendar.TUESDAY, "星期二" to Calendar.TUESDAY,
            "周三" to Calendar.WEDNESDAY, "星期三" to Calendar.WEDNESDAY,
            "周四" to Calendar.THURSDAY, "星期四" to Calendar.THURSDAY,
            "周五" to Calendar.FRIDAY, "星期五" to Calendar.FRIDAY,
            "周六" to Calendar.SATURDAY, "星期六" to Calendar.SATURDAY,
            "周日" to Calendar.SUNDAY, "星期日" to Calendar.SUNDAY, "周天" to Calendar.SUNDAY,
        )
        if (text.contains("今天")) return 0
        if (text.contains("明天")) return 1
        if (text.contains("大后天")) return 3
        if (text.contains("后天")) return 2
        for ((label, dow) in dowMap) {
            if (text.contains(label)) {
                var diff = dow - todayDow
                if (diff <= 0) diff += 7
                if (text.contains("下个") || text.contains("下周")) diff += 7
                return diff
            }
        }
        val dateRegex = Regex("(\\d{1,2})月(\\d{1,2})")
        val match = dateRegex.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        val target = Calendar.getInstance().apply { set(Calendar.MONTH, month - 1); set(Calendar.DAY_OF_MONTH, day) }
        val diffMs = target.timeInMillis - cal.timeInMillis
        val diffDays = (diffMs / (24 * 3600 * 1000)).toInt()
        return if (diffDays in 0..365) diffDays else null
    }

    private fun fetchLocalTimetableForDate(
        application: Application,
        timetableId: String?,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
        dayOffset: Int,
    ): String {
        val cal = Calendar.getInstance()
        val startOfDay = (cal.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfDay = (startOfDay.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

        val input = TimetableAgentInput(
            application = application,
            action = TimetableAgentInput.Action.GET_LOCAL_TIMETABLE,
            timetableId = timetableId,
            fromMs = startOfDay.timeInMillis,
            toMs = endOfDay.timeInMillis,
        )
        return runToolSync(input, agentProvider, onTrace)
    }

    private fun runToolSync(
        input: TimetableAgentInput,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
    ): String {
        val session = agentProvider.createSession()
        val latch = java.util.concurrent.CountDownLatch(1)
        var observation = ""
        var toolError: String? = null
        session.run(input, onTrace) { result ->
            Log.d(TAG, "[DEBUG] Tool result ok=${result.ok} data=${result.data != null} error=${result.error}")
            observation = if (result.ok && result.data != null) {
                formatToolResult(result.data)
            } else {
                toolError = result.error ?: "未知错误"
                "工具执行失败: ${result.error ?: "未知错误"}"
            }
            latch.countDown()
        }
        val success = latch.await(30, java.util.concurrent.TimeUnit.SECONDS)
        if (!success) {
            Log.e(TAG, "[DEBUG] Tool execution timed out after 30s")
            return "工具执行超时"
        }
        if (toolError != null) {
            Log.e(TAG, "[DEBUG] Tool execution failed: $toolError")
        } else {
            Log.d(TAG, "[DEBUG] Tool observation=$observation")
        }
        return observation
    }

    private fun formatToolResult(output: TimetableAgentOutput): String {
        return when (output.action) {
            TimetableAgentInput.Action.GET_LOCAL_TIMETABLE -> {
                if (output.events.isEmpty()) {
                    ""
                } else {
                    val sdf = SimpleDateFormat("HH:mm", Locale.CHINA)
                    buildString {
                        output.events.take(15).forEach { ev ->
                            val from = sdf.format(Date(ev.fromMs))
                            val to = sdf.format(Date(ev.toMs))
                            append("- ${ev.name}  $from~$to")
                            if (ev.place.isNotBlank()) append("  @ ${ev.place}")
                            append("\n")
                        }
                    }
                }
            }

            TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT -> {
                if (output.addedEventIds.isNotEmpty()) "已成功添加 ${output.addedEventIds.size} 个活动。"
                else "活动添加成功。"
            }
        }
    }

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

                val now = Calendar.getInstance()
                val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA).format(Date())
                val currentYear = now.get(Calendar.YEAR)
                val currentMonth = now.get(Calendar.MONTH) + 1
                val currentDay = now.get(Calendar.DAY_OF_MONTH)

                val contextMessage = buildString {
                    append("你是大学生课表助手。当前时间：$dateStr。\n")
                    append("重要提示：当前年份是 $currentYear 年，当前月份是 $currentMonth 月，当前日期是 $currentDay 日。\n")
                    append("计算时间戳时必须使用正确的年份，不要算错年份！\n\n")
                    append("【可用工具】\n")
                    append("1. get_timetable(date_offset: int) - 查询指定日期的课表\n")
                    append("   动作输入示例：{\"date_offset\": 1}\n")
                    append("2. add_activity(name, day_offset, start_time, end_time, place) - 添加活动到本地日历\n")
                    append("   动作输入示例：{\"name\": \"组会\", \"day_offset\": 1, \"start_time\": \"15:00\", \"end_time\": \"17:00\", \"place\": \"A305\"}\n")
                    append("   day_offset: 相对天数偏移，0=今天, 1=明天, 2=后天, -1=昨天, -7=上周今天, 7=下周今天，以此类推\n")
                    append("   start_time/end_time: 24小时制时间（如\"15:00\"、\"09:30\"）\n")
                    append("   绝对不要输出毫秒时间戳！\n")
                    append("3. search_course(query) - 搜索课程信息\n")
                    append("   动作输入示例：{\"query\": \"计算机网络\"}\n")
                    append("   返回：课程列表（含 course_code、名称等）\n")
                    append("   【重要】如果返回多个课程（如\"机器学习\"和\"机器学习导论\"），必须向用户确认用哪个，或分别查询详情\n")
                    append("4. get_course_detail(course_code) - 获取课程详细信息（含评价）\n")
                    append("   动作输入示例：{\"course_code\": \"COMP3003\"}\n")
                    append("   用途：查询课程的具体内容、教师评价、章节信息等\n")
                    append("   流程：先 search_course 找到 course_code → 再用 get_course_detail 查详情\n")
                    append("5. search_teacher(name) - 搜索教师信息\n")
                    append("   动作输入示例：{\"name\": \"秦阳\"}\n")
                    append("6. web_search(query) - 网页搜索\n")
                    append("   动作输入示例：{\"query\": \"OpenAI 最新消息\"}\n")
                    append("7. brave_answer(query) - Brave AI 答案搜索\n")
                    append("   动作输入示例：{\"query\": \"什么是 Transformer\"}\n")
                    append("8. rag_search(query) - 语义搜索知识库（RAG）\n")
                    append("   动作输入示例：{\"query\": \"期末复习重点\"}\n")
                    append("   用途：查询学校相关的知识、历史资料、规章制度、课程经验、校园生活等\n")
                    append("   【强制规则】以下主题必须使用 rag_search，禁止使用 web_search 或 brave_answer：\n")
                    append("   - 学校规章制度、办事流程、学籍管理\n")
                    append("   - 课程经验、考试重点、往届试题、学习资料\n")
                    append("   - 校园生活：宿舍、食堂、图书馆、体育馆、交通\n")
                    append("   - 导师信息、实验室介绍、研究方向\n")
                    append("   - 选课建议、培养方案、学分要求\n")
                    append("   - 只有 rag_search 返回空结果时，才允许 fallback 到 web_search\n")
                    append("9. crawl_page(url) - 爬取单个网页\n")
                    append("   动作输入示例：{\"url\": \"https://example.com\"}\n")
                    append("10. crawl_site(url, max_pages) - 爬取整个网站\n")
                    append("    动作输入示例：{\"url\": \"https://example.com\", \"max_pages\": 10}\n")
                    append("11. crawl_status(task_id) - 查询站点爬取进度\n")
                    append("    动作输入示例：{\"task_id\": \"xxx\"}\n")
                    append("12. submit_review - 提交课程评价/内容\n")
                    append("    支持4种评价类型（通过 review_type 指定）：\n")
                    append("    a) 教师评价（默认）：{\"course_code\": \"COMP3003\", \"content\": \"老师讲得真好\", \"author_name\": \"张三\", \"lecturer_name\": \"李四\"}\n")
                    append("    b) 章节内容：{\"course_code\": \"COMP3003\", \"review_type\": \"section\", \"title\": \"第一章笔记\", \"content\": \"...\", \"author_name\": \"张三\"}\n")
                    append("    c) 多项目课程评价：{\"course_code\": \"COMP3003\", \"review_type\": \"course\", \"course_name\": \"数据结构\", \"title\": \"课程评价\", \"content\": \"...\", \"author_name\": \"张三\"}\n")
                    append("    d) 多项目教师评价：{\"course_code\": \"COMP3003\", \"review_type\": \"course_teacher\", \"course_name\": \"数据结构\", \"teacher_name\": \"李四\", \"content\": \"...\", \"author_name\": \"张三\"}\n")
                    append("12. upload_file(file) - 上传文件到云存储\n")
                    append("13. list_files(prefix) - 列出云存储文件\n")
                    append("14. download_file(key) - 下载云存储文件\n")
                    append("15. delete_file(key) - 删除云存储文件\n")
                    append("16. list_temp_files() - 列出临时文件\n\n")
                    append("【响应格式】\n")
                    append("你必须使用以下格式之一响应：\n\n")
                    append("格式1（需要工具时）：\n")
                    append("思考：[你的推理过程]\n")
                    append("动作：[工具名称]\n")
                    append("动作输入：[JSON格式参数，严格按示例格式]\n\n")
                    append("格式2（直接回答时）：\n")
                    append("思考：[你的推理过程]\n")
                    append("答案：[给用户的最终回复]\n\n")
                    append("【规则】\n")
                    append("- 每次回复必须包含\"思考：\"标签\n")
                    append("- 如果需要工具，必须包含\"动作：\"和\"动作输入：\"\n")
                    append("- 动作输入必须是严格JSON格式，字段名和示例一致\n")
                    append("- add_activity 的 from/to 必须是毫秒级时间戳（纯数字）\n")
                    append("- 如果直接回答，必须包含\"答案：\"\n")
                    append("- 不要输出其他任何格式\n")
                    append("- 绝对不要编造课程信息\n")
                    append("- 禁止使用 XML、HTML、Markdown 代码块等格式\n")
                    append("- 禁止使用 <minimax:tool_call> 或 <invoke> 等标签\n")
                    append("- 必须使用纯文本格式，严格按照上述\"格式1\"或\"格式2\"\n")
                    append("- 工具选择优先级（学校相关问题）：\n")
                    append("  1) 课表/课程安排 → get_timetable\n")
                    append("  2) 课程详情/教师信息 → search_course / search_teacher\n")
                    append("  3) 学校知识/经验/规章制度/校园生活 → rag_search（强制优先，禁止跳过）\n")
                    append("  4) 实时新闻/外部信息 → web_search / brave_answer\n")
                    append("  5) 添加活动/评价 → add_activity / submit_review\n")
                    append("- 重要：对于学校相关问题，必须先尝试 rag_search。如果 rag_search 无结果，再考虑 web_search。\n")
                    append("- 禁止直接对学校相关问题使用 web_search 或 brave_answer 而不先尝试 rag_search。\n")
                    append("- 查询课程评价时：先用 search_course 找到 course_code，再用 get_course_detail 查详情。\n")
                    append("- 如果 search_course 返回多个结果（如\"机器学习\"和\"机器学习导论\"），必须分别查询或向用户确认。\n\n")
                    if (history.size > 1) {
                        append("【对话历史】\n")
                        history.dropLast(1).forEach { msg ->
                            val role = if (msg.role == "user") "用户" else "助手"
                            append("$role：${msg.content.take(500)}\n")
                        }
                        append("\n")
                    }
                    append("【用户当前问题】\n$userMessage")
                }

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

                Log.d(TAG, "[DEBUG] localReAct returned answer length=${answer.length}, thinking length=${thinking.length}")

                val addMatch = addActivityRegex.find(answer)
                if (addMatch != null) {
                    val name = addMatch.groupValues[1]
                    val fromMs = parseTimestampOrIso(addMatch.groupValues[2])
                    val toMs = parseTimestampOrIso(addMatch.groupValues[3])
                    val place = addMatch.groupValues[4]
                    if (name != null && fromMs != null && toMs != null) {
                        val input = TimetableAgentInput(
                            application = application,
                            action = TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT,
                            timetableId = timetableId,
                            arrangement = ArrangementInput(name = name, fromMs = fromMs, toMs = toMs, place = place),
                        )
                        val addResult = runToolSync(input, agentProvider, onTrace)
                        val cleanedAnswer = answer.replace(addMatch.value, "").trim()
                        onResult(LlmChatResult.Success(text = "$cleanedAnswer\n\n$addResult", thinking = thinking))
                        return@withContext
                    }
                }

                Log.d(TAG, "[DEBUG] Calling onResult with Success, answer length=${answer.length}")
                onResult(LlmChatResult.Success(text = answer, thinking = thinking))
                Log.d(TAG, "[DEBUG] onResult called successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Chat failed", e)
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
            Log.d(TAG, "[DEBUG] localReAct step ${step + 1}/10 starting, messages count=${messages.size}")
            onTrace(AgentTraceEvent(stage = "react_step", message = "步骤 ${step + 1}/15: 正在思考…", payload = ""))

            val request = ChatCompletionRequest(
                model = LlmClient.MODEL,
                messages = messages,
            )
            Log.d(TAG, "[DEBUG] Sending MiniMax request with ${messages.size} messages")
            var response: retrofit2.Response<ChatCompletionResponse>? = null
            var retryCount = 0
            val maxRetries = 5
            while (retryCount < maxRetries) {
                val currentResponse = try {
                    LlmClient.service.chatCompletion(LlmClient.authHeader(), request).execute()
                } catch (e: Exception) {
                    Log.e(TAG, "MiniMax API error in step ${step + 1}, attempt ${retryCount + 1}: ${e.javaClass.simpleName}: ${e.message}", e)
                    return null
                }
                response = currentResponse
                if (currentResponse.isSuccessful) break
                val code = currentResponse.code()
                if (code == 529 || code == 503 || code == 429) {
                    retryCount++
                    val delayMs = 3000L * retryCount
                    Log.w(TAG, "MiniMax HTTP $code in step ${step + 1}, attempt $retryCount/$maxRetries, retrying after ${delayMs}ms...")
                    delay(delayMs)
                } else {
                    break
                }
            }
            val finalResponse = response ?: return null

            Log.d(TAG, "[DEBUG] MiniMax response received: isSuccessful=${finalResponse.isSuccessful}, code=${finalResponse.code()}")

            if (!finalResponse.isSuccessful) {
                val errorBody = finalResponse.errorBody()?.string()
                Log.e(TAG, "MiniMax HTTP ${finalResponse.code()} in step ${step + 1}: $errorBody")
                return null
            }

            val body = finalResponse.body()
            Log.d(TAG, "[DEBUG] MiniMax body=${body != null}, choices=${body?.choices?.size}")
            if (body == null) {
                Log.e(TAG, "[DEBUG] MiniMax body is null in step ${step + 1}")
                return null
            }
            val choice = body.choices?.firstOrNull()
            if (choice == null) {
                Log.e(TAG, "[DEBUG] MiniMax choices empty in step ${step + 1}")
                return null
            }
            val content = choice.message?.content
            if (content == null) {
                Log.e(TAG, "[DEBUG] MiniMax content null in step ${step + 1}, message=${choice.message}")
                return null
            }
            val finishReason = choice.finishReason

            Log.d(TAG, "[DEBUG] MiniMax raw content length=${content.length} finish_reason=$finishReason")
            Log.d(TAG, "[DEBUG] MiniMax raw content:\n$content")

            val parsed = parseLocalReActStep(content)
            Log.d(TAG, "[DEBUG] Parsed thought length=${parsed.thought.length} action=${parsed.action} actionInput length=${parsed.actionInput.length}")
            Log.d(TAG, "[DEBUG] Parsed thought:\n${parsed.thought}")

            thinkingSteps.add("步骤 ${step + 1}: ${parsed.thought.take(100)}${if (parsed.action.isNotBlank()) " → ${parsed.action}" else ""}")

            onTrace(AgentTraceEvent(
                stage = "react_step",
                message = "思考: ${parsed.thought.take(80)}${if (parsed.action.isNotBlank()) " → ${parsed.action}" else ""}",
                payload = parsed.actionInput.take(200),
            ))

            if (parsed.action == "答案" || parsed.action.isBlank()) {
                Log.d(TAG, "[DEBUG] Returning final answer length=${parsed.thought.length}")
                val thinking = thinkingSteps.joinToString("\n")
                return Pair(parsed.thought, thinking)
            }

            Log.d(TAG, "[DEBUG] Executing tool action=${parsed.action} input=${parsed.actionInput}")
            val observation = executeLocalTool(
                action = parsed.action,
                actionInput = parsed.actionInput,
                userMessage = userMessage,
                application = application,
                timetableId = timetableId,
                agentProvider = agentProvider,
                onTrace = onTrace,
            ) ?: "工具执行失败或无结果"
            Log.d(TAG, "[DEBUG] Tool observation length=${observation.length}")

            onTrace(AgentTraceEvent(stage = "react_step", message = "观察: ${observation.take(100)}", payload = observation.take(500)))

            messages.add(ChatMessage(role = "assistant", content = content))
            messages.add(ChatMessage(role = "user", content = "观察结果: $observation"))
            Log.d(TAG, "[DEBUG] Appended observation to messages, now ${messages.size} messages")
        }

        val thinking = thinkingSteps.joinToString("\n")
        return Pair("达到最大步骤限制（15步），请简化您的问题", thinking)
    }

    private data class ParsedStep(val thought: String, val action: String, val actionInput: String)

    private data class ParsedActivity(val name: String?, val fromMs: Long?, val toMs: Long?, val place: String)

    private fun parseAddActivityInput(actionInput: String, userMessage: String): ParsedActivity {
        Log.d(TAG, "[DEBUG] parseAddActivityInput actionInput=$actionInput")
        Log.d(TAG, "[DEBUG] actionInput bytes=${actionInput.toByteArray().joinToString(",")}")

        val cleanedInput = actionInput
            .replace(Regex("""```(?:json)?\s*"""), "")
            .replace("```", "")
            .replace("`", "")
            .trim()
        Log.d(TAG, "[DEBUG] cleanedInput=$cleanedInput")

        try {
            val json = org.json.JSONObject(cleanedInput)
            val jsonName = json.optString("name", "").takeIf { it.isNotBlank() }
            val jsonPlace = json.optString("place", "")

            val dayOffset = json.optInt("day_offset", -999)
            val startTime = json.optString("start_time", "")
            val endTime = json.optString("end_time", "")

            if (jsonName != null && dayOffset != -999 && startTime.isNotBlank() && endTime.isNotBlank()) {
                val fromMs = parseRelativeTime(dayOffset, startTime)
                val toMs = parseRelativeTime(dayOffset, endTime)
                if (fromMs != null && toMs != null) {
                    Log.d(TAG, "[DEBUG] Parsed relative time: name=$jsonName fromMs=$fromMs toMs=$toMs")
                    return ParsedActivity(jsonName, fromMs, toMs, jsonPlace)
                }
            }

            val fromVal = json.opt("from")
            val toVal = json.opt("to")
            var jsonFrom: Long? = null
            var jsonTo: Long? = null

            when (fromVal) {
                is Number -> jsonFrom = fromVal.toLong()
                is String -> {
                    val ts = parseTimestampOrIso(fromVal)
                    if (ts != null) jsonFrom = ts
                }
            }

            when (toVal) {
                is Number -> jsonTo = toVal.toLong()
                is String -> {
                    val ts = parseTimestampOrIso(toVal)
                    if (ts != null) jsonTo = ts
                }
            }

            Log.d(TAG, "[DEBUG] JSON parsed: name=$jsonName from=$jsonFrom to=$jsonTo place=$jsonPlace")

            if (jsonName != null && jsonFrom != null && jsonTo != null) {
                Log.d(TAG, "[DEBUG] Parsed from JSON: name=$jsonName fromMs=$jsonFrom toMs=$jsonTo")
                return ParsedActivity(jsonName, jsonFrom, jsonTo, jsonPlace)
            }
        } catch (e: Exception) {
            Log.d(TAG, "[DEBUG] JSON parse failed: ${e.message}")
        }

        Log.d(TAG, "[DEBUG] Failed to parse add_activity input from JSON, trying user message fallback")
        val fallback = parseTimeFromUserMessage(userMessage)
        if (fallback != null && fallback.name != null) {
            return fallback
        }

        return ParsedActivity(null, null, null, "")
    }

    private fun parseRelativeTime(dayOffset: Int, timeStr: String): Long? {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, dayOffset)

        val parts = timeStr.split(":")
        if (parts.size != 2) return null

        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null

        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)

        val result = cal.timeInMillis
        Log.d(TAG, "[DEBUG] parseRelativeTime: dayOffset=$dayOffset time=$timeStr -> $result (${Date(result)})")
        return result
    }

    private fun parseLocalReActStep(text: String): ParsedStep {
        val thoughtRegex = Regex("(?is)思考[：:]\\s*(.+?)(?=\\n动作[：:]|\\n答案[：:]|$)")
        val actionRegex = Regex("(?i)动作[：:]\\s*(\\S+)")
        val actionInputRegex = Regex("(?is)动作输入[：:]\\s*(.+?)(?=\\n思考[：:]|\\n观察[：:]|$)")
        val answerRegex = Regex("(?is)答案[：:]\\s*(.+)")

        val thought = thoughtRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""
        val action = actionRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""
        val actionInput = actionInputRegex.find(text)?.groupValues?.get(1)?.trim() ?: ""

        if (action.isBlank() && thought.isNotBlank()) {
            val answerMatch = answerRegex.find(text)
            if (answerMatch != null) {
                return ParsedStep(thought = answerMatch.groupValues[1].trim(), action = "答案", actionInput = "")
            }
        }

        if (thought.isBlank() && action.isBlank()) {
            Log.d(TAG, "[DEBUG] No ReAct tags found, treating as direct answer")
            return ParsedStep(thought = text.trim(), action = "答案", actionInput = "")
        }

        return ParsedStep(thought = thought, action = action, actionInput = actionInput)
    }

    private fun executeLocalTool(
        action: String,
        actionInput: String,
        userMessage: String,
        application: Application,
        timetableId: String?,
        agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput>,
        onTrace: (AgentTraceEvent) -> Unit,
    ): String? {
        return when (action.lowercase()) {
            "get_timetable" -> {
                val dayOffset = parseDayOffset(userMessage) ?: 0
                val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, dayOffset) }
                val dateLabel = SimpleDateFormat("yyyy-MM-dd EEEE", Locale.CHINA).format(cal.time)
                val info = fetchLocalTimetableForDate(application, timetableId, agentProvider, onTrace, dayOffset)
                if (info.isNotBlank()) "[$dateLabel]\n$info" else "[$dateLabel] 没有课程安排"
            }
            "add_activity" -> {
                val activity = parseAddActivityInput(actionInput, userMessage)
                var name = activity.name
                var fromMs = activity.fromMs
                var toMs = activity.toMs
                var place = activity.place

                if (name == null || fromMs == null || toMs == null) {
                    Log.d(TAG, "[DEBUG] LLM timestamps invalid, attempting to parse from user message")
                    val parsedFromUser = parseTimeFromUserMessage(userMessage)
                    if (parsedFromUser != null) {
                        name = name ?: parsedFromUser.name
                        fromMs = fromMs ?: parsedFromUser.fromMs
                        toMs = toMs ?: parsedFromUser.toMs
                        place = place.ifBlank { parsedFromUser.place }
                        Log.d(TAG, "[DEBUG] Parsed from user message: name=$name fromMs=$fromMs toMs=$toMs place=$place")
                    }
                }

                val now = System.currentTimeMillis()
                if (fromMs != null && toMs != null) {
                    val oneYear = 365L * 24 * 60 * 60 * 1000
                    if (fromMs < now - oneYear || fromMs > now + oneYear ||
                        toMs < now - oneYear || toMs > now + oneYear) {
                        Log.w(TAG, "[DEBUG] Timestamps out of reasonable range, rejecting: fromMs=$fromMs toMs=$toMs")
                        return "时间戳不合理，请重新描述时间（如'明天下午3点'）"
                    }
                }

                if (name != null && fromMs != null && toMs != null) {
                    val input = TimetableAgentInput(
                        application = application,
                        action = TimetableAgentInput.Action.ADD_TIMETABLE_ARRANGEMENT,
                        timetableId = timetableId,
                        arrangement = ArrangementInput(name = name, fromMs = fromMs, toMs = toMs, place = place),
                    )
                    runToolSync(input, agentProvider, onTrace)
                } else "未找到活动信息"
            }
            "search_course" -> {
                val keyword = Regex(""""query"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.replace("""{"query": """, "").replace("""}""", "").trim()
                val result = AgentBackendClient.searchCoursesSync(keyword)
                if (result.ok) "搜索到课程: ${result.results}" else "搜索失败: ${result.error?.message}"
            }
            "get_course_detail" -> {
                val courseCode = Regex(""""course_code"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                val result = AgentBackendClient.readCourseSync(courseCode)
                if (result.ok) {
                    val courseMap = result.course as? Map<*, *> ?: return "课程详情（$courseCode）：暂无详细信息"
                    val courseOutput = courseMap["output"] as? Map<*, *> ?: return "课程详情（$courseCode）：暂无详细信息"
                    val data = courseOutput["data"] as? Map<*, *> ?: return "课程详情（$courseCode）：暂无详细信息"
                    val courseResult = data["result"] as? Map<*, *> ?: return "课程详情（$courseCode）：暂无详细信息"
                    val readmeMd = courseResult["readme_md"] as? String ?: return "课程详情（$courseCode）：暂无详细信息"
                    if (readmeMd.isBlank()) {
                        return "课程详情（$courseCode）：暂无详细信息"
                    }
                    "课程详情（$courseCode）：\n$readmeMd"
                } else {
                    "查询课程详情失败: ${result.error?.message}"
                }
            }
            "search_teacher" -> {
                val name = Regex(""""name"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                val result = AgentBackendClient.searchTeacherSync(name)
                result?.toString() ?: "未找到教师信息"
            }
            "web_search" -> {
                val query = Regex(""""query"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                val result = AgentBackendClient.braveSearchSync(query)
                if (result.ok) {
                    buildString {
                        append("搜索结果 (${result.results.size} 条):\n")
                        result.results.take(5).forEach { r ->
                            append("• ${r["title"]}: ${r["url"]}\n")
                        }
                    }
                } else "搜索失败: ${result.error?.message}"
            }
            "brave_answer" -> {
                val query = Regex(""""query"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                val result = AgentBackendClient.braveAnswerSync(query)
                if (result.ok) {
                    buildString {
                        append("Brave AI 回答:\n")
                        append(result.answer)
                        if (result.model.isNotBlank()) {
                            append("\n\n(模型: ${result.model})")
                        }
                    }
                } else "Brave Answer 失败: ${result.error?.message}"
            }
            "rag_search" -> {
                val query = Regex(""""query"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                AgentBackendClient.ragQuerySync(query) ?: "RAG 查询失败"
            }
            "crawl_page" -> {
                val url = Regex(""""url"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                AgentBackendClient.crawlPageSync(url) ?: "爬取失败"
            }
            "crawl_site" -> {
                val url = Regex(""""url"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                val maxPages = Regex(""""max_pages"\s*:\s*(\d+)"""").find(actionInput)?.groupValues?.get(1)?.toIntOrNull() ?: 10
                AgentBackendClient.crawlSiteSync(url, maxPages) ?: "站点爬取失败"
            }
            "crawl_status" -> {
                val taskId = Regex(""""task_id"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1)
                    ?: actionInput.trim()
                AgentBackendClient.crawlStatusSync(taskId) ?: "查询失败"
            }
            "submit_review" -> {
                val courseCode = Regex(""""course_code"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1) ?: ""
                val content = Regex(""""content"\s*:\s*"([^"]+)"""").find(actionInput)?.groupValues?.get(1) ?: ""
                val authorName = Regex(""""author_name"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: "匿名"
                val lecturerName = Regex(""""lecturer_name"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: ""
                val teacherName = Regex(""""teacher_name"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: ""
                val courseName = Regex(""""course_name"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: ""
                val reviewType = Regex(""""review_type"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: "lecturer"
                val title = Regex(""""title"\s*:\s*"([^"]*)"""").find(actionInput)?.groupValues?.get(1) ?: "课程评价"
                if (courseCode.isBlank() || content.isBlank()) {
                    return "提交评价失败：缺少课程代码或评价内容"
                }
                
                val author = mapOf(
                    "name" to authorName,
                    "link" to "",
                    "date" to SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date()),
                )
                
                val ops = when (reviewType) {
                    "course" -> {
                        if (courseName.isBlank()) {
                            return "提交评价失败：多项目课程评价需要 course_name"
                        }
                        listOf(
                            mapOf(
                                "op" to "add_section_item",
                                "course_name" to courseName,
                                "title" to title,
                                "content" to content,
                                "author" to author,
                            )
                        )
                    }
                    "course_teacher" -> {
                        if (courseName.isBlank() || teacherName.isBlank()) {
                            return "提交评价失败：课程教师评价需要 course_name 和 teacher_name"
                        }
                        listOf(
                            mapOf(
                                "op" to "add_course_teacher_review",
                                "course_name" to courseName,
                                "teacher_name" to teacherName,
                                "content" to content,
                                "author" to author,
                            )
                        )
                    }
                    "section" -> {
                        if (title.isBlank()) {
                            return "提交评价失败：章节内容需要 title"
                        }
                        listOf(
                            mapOf(
                                "op" to "add_section_item",
                                "title" to title,
                                "content" to content,
                                "author" to author,
                            )
                        )
                    }
                    else -> {
                        if (lecturerName.isBlank()) {
                            return "提交评价失败：缺少教师姓名（lecturer_name）"
                        }
                        listOf(
                            mapOf(
                                "op" to "add_lecturer_review",
                                "lecturer_name" to lecturerName,
                                "content" to content,
                                "author" to author,
                            )
                        )
                    }
                }
                Log.d(TAG, "[DEBUG] Calling PrServerClient.submitSync for course=$courseCode, ops=$ops")
                val result = PrServerClient.submitSync(courseCode, ops)
                Log.d(TAG, "[DEBUG] PrServerClient.submitSync result: ok=${result.ok}, data=${result.data}, error=${result.error}")
                if (result.ok) {
                    val prUrl = result.data?.pr?.url
                    if (!prUrl.isNullOrBlank()) {
                        "评价提交成功！PR 链接：$prUrl"
                    } else {
                        "评价提交成功"
                    }
                } else {
                    "评价提交失败：${result.error?.message ?: "未知错误"}"
                }
            }
            "upload_file", "list_files", "download_file", "delete_file", "list_temp_files" -> {
                "文件操作需通过 UI 交互完成，暂不支持纯文本调用"
            }
            else -> "未知工具: $action"
        }
    }

    private fun parseTimeFromUserMessage(userMessage: String): ParsedActivity? {
        val now = Calendar.getInstance()
        val currentYear = now.get(Calendar.YEAR)
        val currentMonth = now.get(Calendar.MONTH) + 1
        val currentDay = now.get(Calendar.DAY_OF_MONTH)

        val tomorrow = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
        val tomorrowYear = tomorrow.get(Calendar.YEAR)
        val tomorrowMonth = tomorrow.get(Calendar.MONTH) + 1
        val tomorrowDay = tomorrow.get(Calendar.DAY_OF_MONTH)

        val nameMatch = Regex("""["']([^"']+)["']""").find(userMessage)
            ?: Regex("""添加.*?["']([^"']+)["']""").find(userMessage)
            ?: Regex("""名为["']([^"']+)["']""").find(userMessage)
        val name = nameMatch?.groupValues?.get(1)

        val placeMatch = Regex("""地点[是为]?\s*["']?([^"'\s]+)["']?""").find(userMessage)
            ?: Regex("""@\s*([A-Z0-9]+)""").find(userMessage)
            ?: Regex("""在\s*([A-Z0-9]+)""").find(userMessage)
        val place = placeMatch?.groupValues?.get(1) ?: ""

        var targetYear = currentYear
        var targetMonth = currentMonth
        var targetDay = currentDay

        when {
            userMessage.contains("明天") || userMessage.contains("明日") -> {
                val t = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
                targetYear = t.get(Calendar.YEAR)
                targetMonth = t.get(Calendar.MONTH) + 1
                targetDay = t.get(Calendar.DAY_OF_MONTH)
            }
            userMessage.contains("后天") -> {
                val t = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 2) }
                targetYear = t.get(Calendar.YEAR)
                targetMonth = t.get(Calendar.MONTH) + 1
                targetDay = t.get(Calendar.DAY_OF_MONTH)
            }
            userMessage.contains("今天") || userMessage.contains("今日") -> {
            }
            Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(userMessage) != null -> {
                val m = Regex("""(\d{1,2})月(\d{1,2})[日号]""").find(userMessage)!!
                targetMonth = m.groupValues[1].toInt()
                targetDay = m.groupValues[2].toInt()
            }
        }

        var hour = 0
        var minute = 0
        var endHour = 0
        var endMinute = 0

        val timeRangeMatch = Regex("""(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?\s*[~-]\s*(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?""").find(userMessage)
        if (timeRangeMatch != null) {
            val startPeriod = timeRangeMatch.groupValues[1]
            val startHour = timeRangeMatch.groupValues[2].toInt()
            val startMinute = timeRangeMatch.groupValues[3].toIntOrNull() ?: 0
            val endPeriod = timeRangeMatch.groupValues[4]
            val endHourVal = timeRangeMatch.groupValues[5].toInt()
            val endMinuteVal = timeRangeMatch.groupValues[6].toIntOrNull() ?: 0

            hour = when {
                startPeriod.contains("下午") && startHour < 12 -> startHour + 12
                startPeriod.contains("晚上") && startHour < 12 -> startHour + 12
                else -> startHour
            }
            minute = startMinute

            endHour = when {
                endPeriod.contains("下午") && endHourVal < 12 -> endHourVal + 12
                endPeriod.contains("晚上") && endHourVal < 12 -> endHourVal + 12
                else -> endHourVal
            }
            endMinute = endMinuteVal
        } else {
            val singleTimeMatch = Regex("""(上午|下午|晚上)?\s*(\d{1,2})[:\u70b9](\d{1,2})?""").find(userMessage)
            if (singleTimeMatch != null) {
                val period = singleTimeMatch.groupValues[1]
                val h = singleTimeMatch.groupValues[2].toInt()
                val m = singleTimeMatch.groupValues[3].toIntOrNull() ?: 0

                hour = when {
                    period.contains("下午") && h < 12 -> h + 12
                    period.contains("晚上") && h < 12 -> h + 12
                    else -> h
                }
                minute = m
                endHour = hour + 1
                endMinute = minute
            }
        }

        if (hour == 0 && minute == 0) return null

        val cal = Calendar.getInstance().apply {
            set(targetYear, targetMonth - 1, targetDay, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val fromMs = cal.timeInMillis

        val endCal = Calendar.getInstance().apply {
            set(targetYear, targetMonth - 1, targetDay, endHour, endMinute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val toMs = endCal.timeInMillis

        Log.d(TAG, "[DEBUG] parseTimeFromUserMessage: name=$name place=$place date=$targetYear-$targetMonth-$targetDay from=$hour:$minute to=$endHour:$endMinute fromMs=$fromMs toMs=$toMs")

        return ParsedActivity(name, fromMs, toMs, place)
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
            android.util.Log.e("LlmChatService", "localLlmGenerate HTTP ${response.code()}")
            return null
        }
        response.body()?.choices?.firstOrNull()?.message?.content?.trim()
    } catch (e: Exception) {
        android.util.Log.e("LlmChatService", "localLlmGenerate error: ${e.message}", e)
        null
    }
}

sealed class LlmChatResult {
    data class Success(val text: String, val thinking: String? = null) : LlmChatResult()
    data class Error(val error: String) : LlmChatResult()
}
