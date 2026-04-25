package com.limpu.hitax.utils

object TermNameFormatter {
    fun shortTermName(termName: String?, fallback: String?): String {
        val primary = termName?.trim().orEmpty()
        if (primary.isNotEmpty()) return primary
        return fallback?.trim().orEmpty()
    }
}
