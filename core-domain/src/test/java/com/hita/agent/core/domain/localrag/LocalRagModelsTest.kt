package com.hita.agent.core.domain.localrag

import org.junit.Assert.assertEquals
import org.junit.Test

class LocalRagModelsTest {
    @Test
    fun localRagHit_hasRequiredFields() {
        val hit = LocalRagHit(
            fileId = "f1",
            chunkId = "c1",
            snippet = "hello",
            score = 1.0,
            displayName = "doc.txt",
            uri = "content://doc"
        )
        assertEquals("f1", hit.fileId)
        assertEquals("c1", hit.chunkId)
    }
}
