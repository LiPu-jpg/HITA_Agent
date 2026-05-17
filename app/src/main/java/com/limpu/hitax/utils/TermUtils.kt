package com.limpu.hitax.utils

import com.limpu.hitax.data.model.eas.TermItem
import java.util.Calendar

object TermUtils {

    private const val RECENT_YEARS = 3

    /**
     * 过滤出最近 [RECENT_YEARS] 年内的学期，按时间倒序排列。
     */
    fun filterRecentTerms(terms: List<TermItem>): List<TermItem> {
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val startYear = currentYear - RECENT_YEARS

        return terms
            .filter { term ->
                val year = term.yearCode.split("-").firstOrNull()?.toIntOrNull() ?: 0
                year >= startYear
            }
            .sortedWith(compareByDescending<TermItem> { it.yearCode }.thenByDescending { it.termCode })
            .distinctBy { it.id }
    }
}
