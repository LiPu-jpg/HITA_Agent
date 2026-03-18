package com.hita.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.remember
import com.hita.agent.ui.MainNavHost

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = remember { AppContainer(this) }
            MaterialTheme {
                Surface {
                    MainNavHost(container)
                }
            }
        }
    }
}
