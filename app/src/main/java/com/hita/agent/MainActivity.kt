package com.hita.agent

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hita.agent.core.ui.components.HitaBottomNavigation
import com.hita.agent.core.ui.components.NavItem
import com.hita.agent.core.ui.theme.HitaAgentTheme
import com.hita.agent.core.ui.theme.HitaColors
import com.hita.agent.ui.emptyrooms.EmptyRoomsScreen
import com.hita.agent.ui.emptyrooms.EmptyRoomsViewModel
import com.hita.agent.ui.scores.ScoresScreen
import com.hita.agent.ui.scores.ScoresViewModel
import com.hita.agent.ui.timetable.TimetableScreen
import com.hita.agent.ui.timetable.TimetableViewModel
import kotlinx.coroutines.launch

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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun MainScreen(container: AppContainer) {
    val pagerState = rememberPagerState(pageCount = { 4 })
    val scope = rememberCoroutineScope()

    val navItems = listOf(
        NavItem("今日", ImageVector.vectorResource(id = R.drawable.ic_nav_today)),
        NavItem("课表", ImageVector.vectorResource(id = R.drawable.ic_nav_timetable)),
        NavItem("工作台", ImageVector.vectorResource(id = R.drawable.ic_nav_workbench)),
        NavItem("功能中心", ImageVector.vectorResource(id = R.drawable.ic_nav_features))
    )

    Scaffold(
        bottomBar = {
            HitaBottomNavigation(
                items = navItems,
                selectedIndex = pagerState.currentPage,
                onItemSelected = { index ->
                    scope.launch {
                        pagerState.animateScrollToPage(index)
                    }
                }
            )
        }
    ) { paddingValues ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.padding(paddingValues),
            userScrollEnabled = true
        ) { page ->
            when (page) {
                0 -> TodayScreen()
                1 -> TimetableScreen(
                    viewModel = viewModel {
                        TimetableViewModel(container.easRepository)
                    }
                )
                2 -> WorkbenchScreen()
                3 -> FeaturesScreen()
            }
        }
    }
}

@Composable
fun TodayScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "今日",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = HitaColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "待实现",
                fontSize = 16.sp,
                color = HitaColors.TextSecondary
            )
        }
    }
}

@Composable
fun WorkbenchScreen(
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "工作台",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = HitaColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "待实现",
                fontSize = 16.sp,
                color = HitaColors.TextSecondary
            )
        }
    }
}

@Composable
fun FeaturesScreen(
    modifier: Modifier = Modifier
) {
    val features = listOf(
        FeatureItem("课表导入", R.drawable.ic_feature_import, HitaColors.Subject1),
        FeatureItem("空教室", R.drawable.ic_feature_rooms, HitaColors.Subject8),
        FeatureItem("成绩查询", R.drawable.ic_feature_scores, HitaColors.Subject3),
        FeatureItem("课程资源查询", R.drawable.ic_feature_resources, HitaColors.Subject5)
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
            .padding(16.dp)
    ) {
        Text(
            text = "功能中心",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = HitaColors.TextPrimary,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(features) { feature ->
                FeatureCard(feature = feature)
            }
        }
    }
}

data class FeatureItem(
    val name: String,
    val iconRes: Int,
    val color: androidx.compose.ui.graphics.Color
)

@Composable
fun FeatureCard(
    feature: FeatureItem,
    onClick: () -> Unit = {}
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.2f)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(feature.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = feature.iconRes),
                    contentDescription = feature.name,
                    tint = feature.color,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = feature.name,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = HitaColors.TextPrimary
            )
        }
    }
}
