package com.hita.agent.core.data.localrag

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hita.agent.core.data.localrag.db.DocChunkEntity
import com.hita.agent.core.data.localrag.db.DocChunkFtsEntity
import com.hita.agent.core.data.localrag.db.IndexedFileEntity
import com.hita.agent.core.data.localrag.db.LocalRagDatabase
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalRagDaoTest {
    private lateinit var db: LocalRagDatabase
    private lateinit var dao: com.hita.agent.core.data.localrag.db.LocalRagDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, LocalRagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.dao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndSearch_returnsHits() {
        dao.insertFile(
            IndexedFileEntity(
                id = "f1",
                uri = "content://doc",
                displayName = "doc.txt",
                mimeType = "text/plain",
                sizeBytes = 10,
                lastModified = null,
                addedAt = 0L,
                status = "INDEXED"
            )
        )
        dao.insertChunks(listOf(DocChunkEntity("c1", "f1", 0, 5)))
        dao.insertFts(listOf(DocChunkFtsEntity("c1", "f1", "hello world")))

        val hits = dao.search("hello", 5)
        assertTrue(hits.isNotEmpty())
    }
}
