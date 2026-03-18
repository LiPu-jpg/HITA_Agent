package com.hita.agent.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hita.agent.AppContainer
import com.hita.agent.R
import com.hita.agent.ui.features.FeaturesScreen
import com.hita.agent.ui.features.FeaturesViewModel
import com.hita.agent.ui.timetable.TimetableScreen
import com.hita.agent.ui.timetable.TimetableViewModel
import com.hita.agent.ui.today.TodayScreen
import com.hita.agent.ui.today.TodayViewModel

@Composable
fun MainNavHost(container: AppContainer) {
    var selectedIndex by rememberSaveable { mutableIntStateOf(0) }

    val items = listOf(
        NavigationItem("今日", ImageVector.vectorResource(id = R.drawable.ic_nav_today)),
        NavigationItem("课表", ImageVector.vectorResource(id = R.drawable.ic_nav_timetable)),
        NavigationItem("功能", ImageVector.vectorResource(id = R.drawable.ic_nav_features))
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        selected = selectedIndex == index,
                        onClick = { selectedIndex = index },
                        label = { Text(item.label) },
                        icon = { Icon(item.icon, contentDescription = item.label) }
                    )
                }
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
                    val viewModel: TodayViewModel = viewModel()
                    TodayScreen(viewModel = viewModel)
                }
                1 -> {
                    val viewModel: TimetableViewModel =
                        viewModel(factory = TimetableViewModel.factory(container.easRepository))
                    TimetableScreen(viewModel = viewModel)
                }
                2 -> {
                    val viewModel: FeaturesViewModel = viewModel()
                    FeaturesScreen(
                        viewModel = viewModel,
                        onTimetableManagerClick = { /* TODO */ },
                        onRecentTimetableClick = { /* TODO */ },
                        onImportTimetableClick = { /* TODO */ },
                        onEmptyClassroomClick = { /* TODO */ },
                        onScoresClick = { /* TODO */ },
                        onExamClick = { /* TODO */ },
                        onCourseLookupClick = { /* TODO */ },
                        onCourseSubmitClick = { /* TODO */ },
                        onProfileClick = { /* TODO */ },
                        onEasLoginClick = { /* TODO */ }
                    )
                }
            }
        }
    }
}

data class NavigationItem(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector
)
