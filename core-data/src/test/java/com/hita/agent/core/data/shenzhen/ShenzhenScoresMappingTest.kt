package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenScoreMapper
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenScoresMappingTest {
    @Test
    fun mapScoresToUnifiedScoreItems() {
        val json = loadJson("shenzhen/scores_list.json")
        val items = ShenzhenScoreMapper.mapScores(json)
        assertTrue(items.any { it.scoreText.isNotBlank() })
    }

    private fun loadJson(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
