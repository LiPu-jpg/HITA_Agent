package com.hita.agent.config

import android.content.Context
import com.hita.agent.core.domain.agent.AgentBackendConfig
import java.io.IOException
import kotlinx.serialization.json.Json

object AgentBackendConfigLoader {
    private val json = Json { ignoreUnknownKeys = true }

    fun load(context: Context, assetName: String = "agent_backend.json"): AgentBackendConfig {
        val content = readAsset(context, assetName)
        return runCatching {
            json.decodeFromString(AgentBackendConfig.serializer(), content)
        }.getOrElse {
            AgentBackendConfig(baseUrl = "http://10.0.2.2:8080", defaultCampusId = "SHENZHEN")
        }
    }

    private fun readAsset(context: Context, assetName: String): String {
        return try {
            context.assets.open(assetName).bufferedReader().use { it.readText() }
        } catch (ex: IOException) {
            ""
        }
    }
}
