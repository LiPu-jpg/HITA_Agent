package com.hita.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.vectorResource
import com.hita.agent.core.ui.components.HitaBottomNavigation
import com.hita.agent.core.ui.components.NavItem
import com.hita.agent.core.ui.theme.HitaAgentTheme
import com.hita.agent.ui.emptyrooms.EmptyRoomsScreen
import com.hita.agent.ui.scores.ScoresScreen
import com.hita.agent.ui.timetable.TimetableScreen
import com.hita.agent.ui.workbench.WorkbenchPlaceholderScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val container = remember { AppContainer(this) }
            HitaAgentTheme {
                MainScreen(container)
            }
        }
    }
}

@Composable
private fun MainScreen(container: AppContainer) {
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val navItems = listOf(
        NavItem("课表", androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_nav_timetable)),
        NavItem("成绩", androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_nav_scores)),
        NavItem("空教室", androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_nav_rooms)),
        NavItem("Agent", androidx.compose.ui.graphics.vector.ImageVector.vectorResource(id = R.drawable.ic_nav_agent))
    )
    
    Scaffold(
        bottomBar = {
            HitaBottomNavigation(
                items = navItems,
                selectedIndex = selectedTab,
                onItemSelected = { selectedTab = it }
            )
        }
    ) { paddingValues ->
        when (selectedTab) {
            0 -> TimetableScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel { 
                    com.hita.agent.ui.timetable.TimetableViewModel(container.easRepository) 
                },
                modifier = Modifier.padding(paddingValues)
            )
            1 -> ScoresScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel { 
                    com.hita.agent.ui.scores.ScoresViewModel(container.easRepository) 
                },
                modifier = Modifier.padding(paddingValues)
            )
            2 -> EmptyRoomsScreen(
                viewModel = androidx.lifecycle.viewmodel.compose.viewModel { 
                    com.hita.agent.ui.emptyrooms.EmptyRoomsViewModel(container.easRepository) 
                },
                modifier = Modifier.padding(paddingValues)
            )
            3 -> WorkbenchPlaceholderScreen(
                modifier = Modifier.padding(paddingValues)
            )
        }
    }
}
