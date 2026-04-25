package com.limpu.hitax.utils

import java.util.Locale

object CourseNameUtils {
    private val bracketSegments = Regex("（[^）]*）|\\([^)]*\\)|【[^】]*】|\\[[^\\]]*]")
    private val bracketChars = Regex("[（）()【】\\[\\]]")
    private val trailingLetter = Regex("[\\s·_.-]*[A-Za-z]+\\d*\\s*$")

    fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val original = raw.trim()
        var name = original
        name = name.replace(bracketSegments, " ")
        name = name.replace(bracketChars, " ")
        name = name.replace(trailingLetter, "")
        name = name.replace("\\s+".toRegex(), " ").trim()
        return if (name.isBlank()) original else name
    }

    fun normalizeKey(raw: String?): String {
        val normalized = normalize(raw) ?: return ""
        return normalized
            .replace("\\s+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }
}
