package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.dto.EmptyRoomOccupancyResponse
import com.hita.agent.core.data.eas.shenzhen.dto.ScoreListResponse
import kotlinx.serialization.json.Json
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenDtoParsingTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun parseEmptyRoomOccupancy() {
        val payload = loadJson("shenzhen/empty_room_occupancy.json")
        val dto = json.decodeFromString<EmptyRoomOccupancyResponse>(payload)
        assertTrue(dto.content.isNotEmpty())
    }

    @Test
    fun parseScoresList() {
        val payload = loadJson("shenzhen/scores_list.json")
        val dto = json.decodeFromString<ScoreListResponse>(payload)
        assertTrue(dto.content.isNotEmpty())
    }

    private fun loadJson(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
