package com.limpu.hitax.agent.core

object AgentTraceSanitizer {
    private val secretPatterns = listOf(
        Regex("(?i)(access[_-]?token\\s*[=:]\\s*)([^,\\s]+)"),
        Regex("(?i)(refresh[_-]?token\\s*[=:]\\s*)([^,\\s]+)"),
        Regex("(?i)(authorization\\s*[=:]\\s*)([^,\\s]+)"),
        Regex("(?i)(cookie\\s*[=:]\\s*)([^,\\s]+)"),
        Regex("(?i)(password\\s*[=:]\\s*)([^,\\s]+)"),
    )

    fun sanitizePayload(payload: String, maxLen: Int = 220): String {
        if (payload.isBlank()) return payload
        var sanitized = payload
        secretPatterns.forEach { regex ->
            sanitized = regex.replace(sanitized) { match ->
                "${match.groupValues[1]}[redacted]"
            }
        }
        return if (sanitized.length > maxLen) {
            sanitized.take(maxLen) + "..."
        } else {
            sanitized
        }
    }

    fun sanitizeError(error: String, maxLen: Int = 160): String {
        return sanitizePayload(error, maxLen)
    }
}
