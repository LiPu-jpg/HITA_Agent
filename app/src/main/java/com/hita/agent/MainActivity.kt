package com.hita.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.remember
import com.hita.agent.core.ui.theme.HitaAgentTheme
import com.hita.agent.ui.MainNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = remember { AppContainer(this) }
            HitaAgentTheme {
                MainNavHost(container)
            }
        }
    }
}
