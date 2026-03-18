package com.hita.agent.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hita.agent.AppContainer
import com.hita.agent.ui.timetable.TimetableScreen
import com.hita.agent.ui.timetable.TimetableViewModel
import com.hita.agent.ui.workbench.WorkbenchRoute

@Composable
fun MainNavHost(container: AppContainer) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedIndex == 0,
                    onClick = { selectedIndex = 0 },
                    label = { Text("Timetable") },
                    icon = {}
                )
                NavigationBarItem(
                    selected = selectedIndex == 1,
                    onClick = { selectedIndex = 1 },
                    label = { Text("Workbench") },
                    icon = {}
                )
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
        ) {
            when (selectedIndex) {
                0 -> {
                    val viewModel: TimetableViewModel =
                        viewModel(factory = TimetableViewModel.factory(container.easRepository))
                    TimetableScreen(viewModel = viewModel)
                }
                else -> {
                    WorkbenchRoute(container = container)
                }
            }
        }
    }
}
