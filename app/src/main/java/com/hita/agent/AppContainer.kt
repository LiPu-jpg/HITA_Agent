package com.hita.agent

import android.content.Context
import com.hita.agent.config.AgentBackendConfigLoader
import com.hita.agent.core.data.DebugLog
import com.hita.agent.core.data.agent.AgentBackendApi
import com.hita.agent.core.data.eas.shenzhen.EasShenzhenAdapter
import com.hita.agent.core.data.localrag.LocalRagRepository
import com.hita.agent.core.data.localrag.db.LocalRagDatabase
import com.hita.agent.core.data.repo.EasRepositoryImpl
import com.hita.agent.core.data.store.FileCacheStore
import com.hita.agent.core.data.store.FileSessionStore
import com.hita.agent.core.domain.agent.PlannerConfig
import java.io.File

class AppContainer(context: Context) {
    private val cacheDir = File(context.cacheDir, "eas_cache")
    private val sessionDir = File(context.filesDir, "sessions")

    init {
        DebugLog.enabled = BuildConfig.DEBUG
    }

    private val cacheStore = FileCacheStore(cacheDir)
    val sessionStore = FileSessionStore(sessionDir)
    private val adapter = EasShenzhenAdapter()
    private val agentBackendConfig = AgentBackendConfigLoader.load(context)
    val agentBackendApi = AgentBackendApi.create(agentBackendConfig)
    val plannerConfig = PlannerConfig.from(agentBackendConfig)
    private val localRagDb = LocalRagDatabase.build(context)
    val localRagRepository = LocalRagRepository(localRagDb, context.contentResolver)

    val easRepository = EasRepositoryImpl(
        adapter = adapter,
        sessionStore = sessionStore,
        cacheStore = cacheStore
    )
}
