package com.limpu.hitax.agent.llm

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Builds the ReAct system prompt from `assets/react_system_prompt.txt`. */
object ReactPromptBuilder {

    private const val TEMPLATE_PATH = "react_system_prompt.txt"
    private var cachedTemplate: String? = null

    fun build(
        context: Context,
        userMessage: String,
        history: List<ChatMessage>,
    ): String {
        val template = loadTemplate(context)
        val now = Calendar.getInstance()

        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss EEEE", Locale.CHINA)
        dateFormat.timeZone = TimeZone.getDefault()

        val dateStr = dateFormat.format(Date())
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH) + 1
        val day = now.get(Calendar.DAY_OF_MONTH)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val minute = now.get(Calendar.MINUTE)

        var prompt = template
            .replace("{{DATE}}", dateStr)
            .replace("{{YEAR}}", year.toString())
            .replace("{{MONTH}}", month.toString())
            .replace("{{DAY}}", day.toString())
            .replace("{{HOUR}}", hour.toString())
            .replace("{{MINUTE}}", minute.toString())
            .replace("{{USER_MESSAGE}}", userMessage)

        if (history.size > 1) {
            val historySection = buildString {
                append("【对话历史】\n")
                history.dropLast(1).forEach { msg ->
                    val role = if (msg.role == "user") "用户" else "助手"
                    append("$role：${msg.content.take(500)}\n")
                }
                append("\n")
            }
            prompt = prompt.replace("{{HISTORY}}", historySection)
        } else {
            prompt = prompt.replace("{{HISTORY}}", "")
        }

        return prompt
    }

    private fun loadTemplate(context: Context): String {
        return cachedTemplate ?: run {
            val inputStream = context.assets.open(TEMPLATE_PATH)
            val template = inputStream.bufferedReader().use { it.readText() }
            cachedTemplate = template
            template
        }
    }

    fun invalidateCache() {
        cachedTemplate = null
    }
}
