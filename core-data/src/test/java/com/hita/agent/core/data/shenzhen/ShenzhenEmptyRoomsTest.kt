package com.hita.agent.core.data.shenzhen

import com.hita.agent.core.data.eas.shenzhen.mapper.ShenzhenEmptyRoomMapper
import org.junit.Assert.assertTrue
import org.junit.Test

class ShenzhenEmptyRoomsTest {
    @Test
    fun filterEmptyRoomsByPeriod() {
        val json = loadJson("shenzhen/empty_room_occupancy.json")
        val rooms = ShenzhenEmptyRoomMapper.filter(json, period = "DJ2")
        assertTrue(rooms.all { it.status.name == "FREE" || it.status.name == "OCCUPIED" })
    }

    private fun loadJson(path: String): String {
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Missing resource: $path")
        return stream.bufferedReader().use { it.readText() }
    }
}
