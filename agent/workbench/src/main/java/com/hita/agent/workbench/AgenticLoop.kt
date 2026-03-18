package com.hita.agent.workbench

import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.data.agent.AgentBackendApi
import com.hita.agent.core.data.localrag.LocalRagRepository
import com.hita.agent.core.domain.agent.ApiError
import com.hita.agent.core.domain.agent.InvokeResponse
import com.hita.agent.core.domain.agent.JobResponse
import com.hita.agent.core.domain.agent.JobStatus
import com.hita.agent.core.domain.agent.PlannerAction
import com.hita.agent.core.domain.agent.PlannerConfig
import com.hita.agent.core.domain.agent.PlannerDecision
import com.hita.agent.core.domain.agent.RagQueryInput
import com.hita.agent.core.domain.agent.RagQueryOutput
import com.hita.agent.core.domain.agent.SearchOutput
import com.hita.agent.core.data.llm.LlmClient
import com.hita.agent.core.domain.model.UnifiedTerm
import com.hita.agent.core.domain.repo.EasRepository
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class AgenticLoop(
    private val api: AgentBackendApi,
    private val easRepository: EasRepository?,
    private val plannerConfig: PlannerConfig,
    private val localRagRepository: LocalRagRepository?
) {
    private var localSummary: String? = null

    suspend fun run(prompt: String, trace: JsonElement): List<AgenticEvent> {
        DebugLog.d("AgenticLoop", "plan start len=${prompt.length}")
        val plan = Planner(plannerConfig).plan(prompt, api, localSummary)
        DebugLog.d("AgenticLoop", "plan steps=${plan.steps.size}")
        val events = mutableListOf<AgenticEvent>()
        for (step in plan.steps) {
            DebugLog.d("AgenticLoop", "step=${step.describe()}")
            events.add(AgenticEvent.Action(step.describe()))
            val observation = executeStep(step, trace)
            events.add(AgenticEvent.Observation(observation))
            if (step is AgentStep.NeedInput) break
            if (observation.startsWith("error:")) break
        }
        return events
    }

    private suspend fun executeStep(step: AgentStep, trace: JsonElement): String {
        return when (step) {
            is AgentStep.PublicSkill -> runPublicSkill(step, trace)
            is AgentStep.LocalScores -> runLocalScores(step)
            is AgentStep.LocalTimetable -> runLocalTimetable(step)
            is AgentStep.LocalEmptyRooms -> runLocalEmptyRooms(step)
            is AgentStep.LocalRag -> runLocalRag(step)
            is AgentStep.NeedInput -> step.message
            is AgentStep.SystemNote -> step.message
        }
    }

    private suspend fun runPublicSkill(step: AgentStep.PublicSkill, trace: JsonElement): String {
        val response = runCatching { api.invokeSkill(step.name, step.input, trace) }
            .getOrElse { ex ->
                DebugLog.w("AgenticLoop", "invoke failed name=${step.name}", ex)
                return "error: ${ex.message ?: "network error"}"
            }
        if (!response.ok) {
            DebugLog.d("AgenticLoop", "invoke failed name=${step.name}")
            return "error: ${response.error?.message ?: "skill failed"}"
        }
        val jobId = response.jobId
        if (!jobId.isNullOrBlank()) {
            DebugLog.d("AgenticLoop", "job queued name=${step.name} id=$jobId")
            val job = pollJob(jobId)
            return handleJobResponse(job)
        }
        val output = response.output ?: JsonNull
        DebugLog.d("AgenticLoop", "invoke ok name=${step.name}")
        return renderPublicOutput(step.name, output)
    }

    private suspend fun pollJob(jobId: String): JobResponse {
        val delays = listOf(500L, 1000L, 2000L, 5000L)
        val start = System.currentTimeMillis()
        var index = 0
        DebugLog.d("AgenticLoop", "poll job start id=$jobId")
        while (System.currentTimeMillis() - start < 120_000) {
            val response = runCatching { api.getJob(jobId) }
                .getOrElse { ex ->
                    DebugLog.w("AgenticLoop", "poll job error id=$jobId", ex)
                    return JobResponse(ok = false, error = ApiError("NETWORK", ex.message ?: "network error"))
                }
            if (!response.ok) return response
            val status = response.job?.status
            if (status == JobStatus.SUCCEEDED || status == JobStatus.FAILED) {
                DebugLog.d("AgenticLoop", "poll job done id=$jobId status=$status")
                return response
            }
            delay(delays[index])
            if (index < delays.lastIndex) index++
        }
        DebugLog.d("AgenticLoop", "poll job timeout id=$jobId")
        return JobResponse(ok = false, error = ApiError("JOB_TIMEOUT", "job polling timed out", true))
    }

    private fun handleJobResponse(response: JobResponse): String {
        if (!response.ok) {
            return "error: ${response.error?.message ?: "job error"}"
        }
        val job = response.job ?: return "error: missing job payload"
        if (job.status == JobStatus.FAILED) {
            return "error: ${job.error?.message ?: "job failed"}"
        }
        val output = job.outputJson ?: JsonNull
        return renderPublicOutput(job.skillName, output)
    }

    private fun renderPublicOutput(skillName: String, output: JsonElement): String {
        return when (skillName) {
            "rag.query" -> {
                val rag = runCatching { api.decodeRagQueryOutput(output) }.getOrNull()
                rag?.let { renderRag(it) } ?: "No results"
            }
            "search" -> {
                val search = runCatching { api.decodeSearchOutput(output) }.getOrNull()
                search?.let { renderSearch(it) } ?: "result: ${output.toString().take(200)}"
            }
            else -> "result: ${output.toString().take(200)}"
        }
    }

    private fun renderSearch(output: SearchOutput): String {
        if (output.results.isEmpty()) return "No results found for: ${output.query}"
        val builder = StringBuilder()
        builder.append("Search results for \"${output.query}\" (${output.total} total):\n\n")
        output.results.take(10).forEachIndexed { index, result ->
            val title = result.title ?: "Untitled"
            val source = result.source
            val content = result.content?.replace("\n", " ")?.take(150) ?: ""
            builder.append("${index + 1}. [$source] $title\n")
            if (content.isNotBlank()) {
                builder.append("   $content\n")
            }
        }
        output.summary?.let { summary ->
            builder.append("\nSummary: ${summary.take(300)}\n")
        }
        return builder.toString().trimEnd()
    }

    private fun renderRag(output: RagQueryOutput): String {
        if (output.hits.isEmpty()) return "No hits"
        val builder = StringBuilder()
        builder.append("Top hits:\n")
        output.hits.take(5).forEachIndexed { index, hit ->
            val title = hit.title ?: hit.docId
            val snippet = hit.snippet ?: ""
            builder.append("${index + 1}. $title\n")
            if (snippet.isNotBlank()) {
                builder.append("   ${snippet.replace("\n", " ").take(120)}\n")
            }
        }
        return builder.toString().trimEnd()
    }

    private suspend fun runLocalScores(step: AgentStep.LocalScores): String {
        val repo = easRepository ?: return "error: local repository unavailable"
        return runCatching {
            val term = resolveCurrentTerm(repo)
            val result = repo.getScores(term, step.qzqmFlag, forceRefresh = step.forceRefresh)
            val count = result.data.size
            val stale = if (result.stale) " (stale)" else ""
            localSummary = "scores_cache: count=$count, stale=${result.stale}, source=${result.source}"
            DebugLog.d("AgenticLoop", "local scores count=$count stale=${result.stale}")
            "scores: $count items$stale"
        }.getOrElse { ex ->
            DebugLog.w("AgenticLoop", "local scores failed", ex)
            "error: ${ex.message ?: "local scores failed"}"
        }
    }

    private suspend fun runLocalTimetable(step: AgentStep.LocalTimetable): String {
        val repo = easRepository ?: return "error: local repository unavailable"
        return runCatching {
            val term = resolveCurrentTerm(repo)
            val result = repo.getTimetable(term, forceRefresh = step.forceRefresh)
            val count = result.data.size
            val stale = if (result.stale) " (stale)" else ""
            localSummary = "timetable_cache: count=$count, stale=${result.stale}, source=${result.source}"
            DebugLog.d("AgenticLoop", "local timetable count=$count stale=${result.stale}")
            "timetable: $count items$stale"
        }.getOrElse { ex ->
            DebugLog.w("AgenticLoop", "local timetable failed", ex)
            "error: ${ex.message ?: "local timetable failed"}"
        }
    }

    private suspend fun runLocalEmptyRooms(step: AgentStep.LocalEmptyRooms): String {
        val repo = easRepository ?: return "error: local repository unavailable"
        return runCatching {
            val result = repo.getEmptyRooms(step.date, step.buildingId, step.period, forceRefresh = step.forceRefresh)
            val count = result.rooms.size
            localSummary = "empty_rooms_cache: count=$count, date=${step.date}, period=${step.period}"
            DebugLog.d("AgenticLoop", "local empty rooms count=$count date=${step.date} period=${step.period}")
            "empty rooms: $count"
        }.getOrElse { ex ->
            DebugLog.w("AgenticLoop", "local empty rooms failed", ex)
            "error: ${ex.message ?: "local empty rooms failed"}"
        }
    }

    private suspend fun runLocalRag(step: AgentStep.LocalRag): String {
        val repo = localRagRepository ?: return "error: local rag unavailable"
        return runCatching {
            val hits = repo.search(step.query, topK = 5)
            if (hits.isEmpty()) return@runCatching "local rag: no results"
            val builder = StringBuilder()
            builder.append("Local hits:\n")
            hits.forEachIndexed { index, hit ->
                val title = hit.displayName ?: hit.uri
                builder.append("${index + 1}. $title\n")
                if (hit.snippet.isNotBlank()) {
                    builder.append("   ${hit.snippet.replace("\n", " ").take(120)}\n")
                }
            }
            DebugLog.d("AgenticLoop", "local rag hits=${hits.size}")
            builder.toString().trimEnd()
        }.getOrElse { ex ->
            DebugLog.w("AgenticLoop", "local rag failed", ex)
            "error: ${ex.message ?: "local rag failed"}"
        }
    }

    private suspend fun resolveCurrentTerm(repo: EasRepository): UnifiedTerm {
        val terms = repo.getTerms()
        return terms.firstOrNull { it.isCurrent } ?: terms.first()
    }

    class Planner(private val config: PlannerConfig) {
        private val llmPlanner = LlmPlanner(config)

        fun plan(prompt: String, api: AgentBackendApi, localSummary: String?): AgentPlan {
            val llmPlan = llmPlanner.plan(prompt, api, localSummary)
            if (llmPlan != null) return llmPlan
            val input = api.json.encodeToJsonElement(mapOf(
                "query" to prompt,
                "top_k" to 10,
                "sources" to listOf("rag", "brave", "course", "hit_teacher")
            ))
            return AgentPlan(
                steps = listOf(
                    AgentStep.SystemNote("planner: llm not configured, fallback to unified search"),
                    AgentStep.PublicSkill("search", input)
                )
            )
        }
    }

    class RulePlanner {
        fun plan(prompt: String, api: AgentBackendApi): AgentPlan {
            val normalized = prompt.lowercase()
            return when {
                containsAny(normalized, listOf("成绩", "score", "grade", "gpa")) ->
                    AgentPlan(listOf(AgentStep.LocalScores(qzqmFlag = "qm")))
                containsAny(normalized, listOf("课表", "timetable", "schedule")) ->
                    AgentPlan(listOf(AgentStep.LocalTimetable()))
                containsAny(normalized, listOf("空教室", "empty room")) -> {
                    val date = extractDate(prompt) ?: todayDate()
                    val period = extractPeriod(prompt) ?: "DJ1"
                    val buildingId = extractBuildingId(prompt)
                    if (buildingId.isNullOrBlank()) {
                        AgentPlan(
                            listOf(
                                AgentStep.NeedInput(
                                    "need buildingId. provide building code and optionally date (YYYY-MM-DD) and period (DJ1..DJ6). default date=$date, period=$period."
                                )
                            )
                        )
                    } else {
                        AgentPlan(listOf(AgentStep.LocalEmptyRooms(date = date, buildingId = buildingId, period = period)))
                    }
                }
                containsAny(normalized, listOf("文件", "文档", "资料", "笔记", "document", "file", "notes")) ->
                    AgentPlan(listOf(AgentStep.LocalRag(prompt)))
                else -> {
                    val input = api.json.encodeToJsonElement(mapOf(
                        "query" to prompt,
                        "top_k" to 10,
                        "sources" to listOf("rag", "brave", "course", "hit_teacher")
                    ))
                    AgentPlan(listOf(AgentStep.PublicSkill("search", input)))
                }
            }
        }

        fun todayDate(): String {
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            return formatter.format(Instant.now().atZone(ZoneId.systemDefault()).toLocalDate())
        }

        private fun extractDate(text: String): String? {
            val regex = Regex("\\b(20\\d{2}-\\d{2}-\\d{2})\\b")
            return regex.find(text)?.groupValues?.getOrNull(1)
        }

        private fun extractPeriod(text: String): String? {
            val regex = Regex("\\bDJ[1-6]\\b", RegexOption.IGNORE_CASE)
            return regex.find(text)?.value?.uppercase()
        }

        private fun extractBuildingId(text: String): String? {
            val regex = Regex("(?:教学楼|building)\\s*([A-Za-z0-9]+)", RegexOption.IGNORE_CASE)
            return regex.find(text)?.groupValues?.getOrNull(1)
        }

        private fun containsAny(text: String, keywords: List<String>): Boolean {
            return keywords.any { text.contains(it) }
        }
    }

    class LlmPlanner(private val config: PlannerConfig) {
        fun plan(prompt: String, api: AgentBackendApi, localSummary: String?): AgentPlan? {
            if (!config.isLlmConfigured()) return null
            val client = LlmClient(config)
            val sanitized = sanitize(prompt)
            val summary = localSummary?.let { "Local summary (redacted): $it" } ?: "Local summary: none"
            
            // 获取可用 skills 列表
            val skillsResponse = runCatching { api.listSkills() }.getOrNull()
            val availableSkills = skillsResponse?.skills?.joinToString(", ") ?: "rag.query"
            
            val system = """
You are a routing planner for a student assistant.
Output ONLY compact JSON with keys: action, date, period, building_id, skill_name, skill_input.
Actions: local_scores, local_timetable, local_empty_rooms, local_rag, public_rag, public_skill.
For public_skill, provide skill_name and skill_input (JSON object).
Available skills: $availableSkills
If unsure, choose public_rag.
""".trimIndent()
            val user = """
Classify user intent and return JSON only.
$summary
User: $sanitized
""".trimIndent()
            val content = client.completeStream(system, user) ?: return null
            val decision = parseDecision(content, api) ?: return null
            return decisionToPlan(decision, api, prompt)
        }

        private fun parseDecision(content: String, api: AgentBackendApi): PlannerDecision? {
            val json = runCatching { apiJson.parseToJsonElement(content).jsonObject }.getOrNull() ?: return null
            val actionRaw = json["action"]?.jsonPrimitive?.content?.lowercase() ?: return null
            val action = when (actionRaw) {
                "local_scores" -> PlannerAction.LOCAL_SCORES
                "local_timetable" -> PlannerAction.LOCAL_TIMETABLE
                "local_empty_rooms" -> PlannerAction.LOCAL_EMPTY_ROOMS
                "local_rag" -> PlannerAction.LOCAL_RAG
                "public_rag" -> PlannerAction.PUBLIC_RAG
                "public_skill" -> PlannerAction.PUBLIC_SKILL
                else -> PlannerAction.PUBLIC_RAG
            }
            val date = json["date"]?.jsonPrimitive?.content
            val period = json["period"]?.jsonPrimitive?.content
            val buildingId = json["building_id"]?.jsonPrimitive?.content
            val skillName = json["skill_name"]?.jsonPrimitive?.content
            val skillInput = json["skill_input"]?.jsonObject?.let { api.json.encodeToJsonElement(it) }
            return PlannerDecision(action, date, period, buildingId, skillName, skillInput)
        }

        private fun decisionToPlan(decision: PlannerDecision, api: AgentBackendApi, prompt: String): AgentPlan {
            return when (decision.action) {
                PlannerAction.LOCAL_SCORES -> AgentPlan(listOf(AgentStep.LocalScores(qzqmFlag = "qm")))
                PlannerAction.LOCAL_TIMETABLE -> AgentPlan(listOf(AgentStep.LocalTimetable()))
                PlannerAction.LOCAL_EMPTY_ROOMS -> {
                    val date = decision.date ?: RulePlanner().todayDate()
                    val period = decision.period ?: "DJ1"
                    val buildingId = decision.buildingId
                    if (buildingId.isNullOrBlank()) {
                        AgentPlan(listOf(AgentStep.NeedInput("need buildingId for empty rooms")))
                    } else {
                        AgentPlan(listOf(AgentStep.LocalEmptyRooms(date, buildingId, period)))
                    }
                }
                PlannerAction.LOCAL_RAG -> {
                    AgentPlan(listOf(AgentStep.LocalRag(prompt)))
                }
                PlannerAction.PUBLIC_RAG -> {
                    val input = api.json.encodeToJsonElement(mapOf(
                        "query" to prompt,
                        "top_k" to 10,
                        "sources" to listOf("rag", "brave", "course", "hit_teacher")
                    ))
                    AgentPlan(listOf(AgentStep.PublicSkill("search", input)))
                }
                PlannerAction.PUBLIC_SKILL -> {
                    // 新增：支持调用任意 public skill
                    val skillName = decision.skillName
                    val skillInput = decision.skillInput
                    if (skillName.isNullOrBlank()) {
                        AgentPlan(listOf(AgentStep.NeedInput("need skill_name for public_skill")))
                    } else {
                        AgentPlan(listOf(AgentStep.PublicSkill(skillName, skillInput ?: api.json.encodeToJsonElement(emptyMap<String, String>()))))
                    }
                }
            }
        }

        private fun sanitize(text: String): String {
            val idLike = Regex("\\b\\d{8,}\\b")
            return text.replace(idLike, "<redacted>")
        }

        private val apiJson = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
    }
}
sealed class AgentStep {
    data class PublicSkill(val name: String, val input: JsonElement) : AgentStep()
    data class LocalScores(val qzqmFlag: String, val forceRefresh: Boolean = false) : AgentStep()
    data class LocalTimetable(val forceRefresh: Boolean = false) : AgentStep()
    data class LocalEmptyRooms(
        val date: String,
        val buildingId: String,
        val period: String,
        val forceRefresh: Boolean = false
    ) : AgentStep()
    data class LocalRag(val query: String) : AgentStep()
    data class NeedInput(val message: String) : AgentStep()
    data class SystemNote(val message: String) : AgentStep()

    fun describe(): String = when (this) {
        is PublicSkill -> "call public skill: $name"
        is LocalScores -> "fetch local scores ($qzqmFlag)"
        is LocalTimetable -> "fetch local timetable"
        is LocalEmptyRooms -> "fetch empty rooms"
        is LocalRag -> "search local documents"
        is NeedInput -> "need input"
        is SystemNote -> "note"
    }
}

data class AgentPlan(
    val steps: List<AgentStep>
)

sealed class AgenticEvent {
    data class Action(val text: String) : AgenticEvent()
    data class Observation(val text: String) : AgenticEvent()
}
