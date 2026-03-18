package com.hita.agent.core.data.localrag

import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkerTest {
    @Test
    fun chunker_splitsTextIntoFixedSizes() {
        val text = "a".repeat(2500)
        val chunks = Chunker(chunkSize = 1000).chunk(text)
        assertEquals(3, chunks.size)
        assertEquals(0, chunks[0].start)
        assertEquals(1000, chunks[0].end)
    }
}
