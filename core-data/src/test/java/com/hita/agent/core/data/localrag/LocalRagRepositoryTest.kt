package com.hita.agent.core.data.localrag

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hita.agent.core.data.localrag.db.LocalRagDatabase
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalRagRepositoryTest {
    @Test
    fun indexAndSearch_returnsHit() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalRagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val parser = object : DocumentParser {
            override fun canHandle(mimeType: String?, name: String?): Boolean = true
            override fun parse(input: java.io.InputStream): String = input.bufferedReader().readText()
        }
        val repo = LocalRagRepository(db, context.contentResolver, ParserRegistry(listOf(parser)))
        val file = File(context.cacheDir, "doc.txt")
        file.writeText("hello world")
        val uri = Uri.fromFile(file)

        repo.indexFile(uri)
        val hits = repo.search("hello", 3)
        assertTrue(hits.isNotEmpty())

        db.close()
    }
}
