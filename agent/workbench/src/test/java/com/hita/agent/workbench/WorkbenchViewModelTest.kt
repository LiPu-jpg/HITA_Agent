package com.hita.agent.workbench

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hita.agent.core.data.agent.AgentBackendApi
import com.hita.agent.core.data.localrag.DocumentParser
import com.hita.agent.core.data.localrag.LocalRagRepository
import com.hita.agent.core.data.localrag.ParserRegistry
import com.hita.agent.core.data.localrag.db.LocalRagDatabase
import com.hita.agent.core.domain.agent.AgentBackendConfig
import com.hita.agent.core.domain.agent.PlannerConfig
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class WorkbenchViewModelTest {
    @get:Rule val dispatcherRule = MainDispatcherRule()

    @Test
    fun onUploadSelected_indexesFileAndReports() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, LocalRagDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val parser = object : DocumentParser {
            override fun canHandle(mimeType: String?, name: String?): Boolean = true
            override fun parse(input: java.io.InputStream): String = input.bufferedReader().readText()
        }
        val repo = LocalRagRepository(
            db = db,
            contentResolver = context.contentResolver,
            parserRegistry = ParserRegistry(listOf(parser)),
            ioDispatcher = dispatcherRule.dispatcher
        )
        val api = AgentBackendApi.create(AgentBackendConfig(baseUrl = "http://localhost"))
        val viewModel = WorkbenchViewModel(api, null, PlannerConfig(), repo)

        val file = File(context.cacheDir, "doc.txt")
        file.writeText("hello world")
        val uri = Uri.fromFile(file)

        viewModel.onUploadSelected(uri)
        advanceUntilIdle()

        val last = viewModel.state.value.messages.last().content
        assertTrue(last.contains("Indexed"))

        db.close()
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher()
) : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
