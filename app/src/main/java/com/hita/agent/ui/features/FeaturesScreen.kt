package com.hita.agent.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.res.vectorResource
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hita.agent.core.ui.theme.HitaColors

// Data Models
data class UserInfo(
    val isLoggedIn: Boolean,
    val username: String,
    val nickname: String,
    val avatarUrl: String?,
    val easUsername: String?,
    val isEasLoggedIn: Boolean
)

data class TimetableInfo(
    val recentTimetableName: String?,
    val timetableCount: Int
)

sealed class FeaturesUiState {
    object Loading : FeaturesUiState()
    data class Content(
        val userInfo: UserInfo,
        val timetableInfo: TimetableInfo
    ) : FeaturesUiState()
}

@Composable
fun FeaturesScreen(
    viewModel: FeaturesViewModel,
    onTimetableManagerClick: () -> Unit,
    onRecentTimetableClick: () -> Unit,
    onImportTimetableClick: () -> Unit,
    onEmptyClassroomClick: () -> Unit,
    onScoresClick: () -> Unit,
    onExamClick: () -> Unit,
    onCourseLookupClick: () -> Unit,
    onCourseSubmitClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEasLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.uiState.collectAsState()
    FeaturesScreenContent(
        state = state,
        onTimetableManagerClick = onTimetableManagerClick,
        onRecentTimetableClick = onRecentTimetableClick,
        onImportTimetableClick = onImportTimetableClick,
        onEmptyClassroomClick = onEmptyClassroomClick,
        onScoresClick = onScoresClick,
        onExamClick = onExamClick,
        onCourseLookupClick = onCourseLookupClick,
        onCourseSubmitClick = onCourseSubmitClick,
        onProfileClick = onProfileClick,
        onEasLoginClick = onEasLoginClick,
        modifier = modifier
    )
}

@Composable
fun FeaturesScreenContent(
    state: FeaturesUiState,
    onTimetableManagerClick: () -> Unit,
    onRecentTimetableClick: () -> Unit,
    onImportTimetableClick: () -> Unit,
    onEmptyClassroomClick: () -> Unit,
    onScoresClick: () -> Unit,
    onExamClick: () -> Unit,
    onCourseLookupClick: () -> Unit,
    onCourseSubmitClick: () -> Unit,
    onProfileClick: () -> Unit,
    onEasLoginClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(HitaColors.BackgroundBottom)
            .verticalScroll(rememberScrollState())
    ) {
        when (state) {
            is FeaturesUiState.Loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = HitaColors.Primary)
                }
            }
            is FeaturesUiState.Content -> {
                // User Profile Card
                UserProfileCard(
                    userInfo = state.userInfo,
                    onClick = onProfileClick
                )

                // Timetable Quick Access Cards
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    QuickAccessCard(
                        title = "最近课表",
                        subtitle = state.timetableInfo.recentTimetableName ?: "无",
                        icon = Icons.Default.DateRange,
                        onClick = onRecentTimetableClick,
                        modifier = Modifier.weight(1f)
                    )
                    QuickAccessCard(
                        title = "课表管理",
                        subtitle = if (state.timetableInfo.timetableCount == 0) "暂无课表"
                        else "${state.timetableInfo.timetableCount}个课表",
                        icon = Icons.Default.List,
                        onClick = onTimetableManagerClick,
                        modifier = Modifier.weight(1f)
                    )
                }

                // EAS Section
                EasSection(
                    isLoggedIn = state.userInfo.isEasLoggedIn,
                    username = state.userInfo.easUsername,
                    onLoginClick = onEasLoginClick,
                    onImportClick = onImportTimetableClick,
                    onExamClick = onExamClick,
                    onEmptyClassroomClick = onEmptyClassroomClick,
                    onScoresClick = onScoresClick
                )

                // Course Resources Section
                CourseResourcesSection(
                    onCourseLookupClick = onCourseLookupClick,
                    onCourseSubmitClick = onCourseSubmitClick
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun UserProfileCard(
    userInfo: UserInfo,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(HitaColors.BackgroundBottom),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Avatar",
                    modifier = Modifier.size(32.dp),
                    tint = HitaColors.TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // User info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (userInfo.isLoggedIn) userInfo.nickname else userInfo.username,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = HitaColors.TextPrimary,
                    maxLines = 1
                )
                if (userInfo.isLoggedIn) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = userInfo.username,
                        fontSize = 14.sp,
                        color = HitaColors.TextSecondary.copy(alpha = 0.6f),
                        maxLines = 1
                    )
                }
            }

            // Arrow
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = HitaColors.PrimaryDisabled
            )
        }
    }
}

@Composable
private fun QuickAccessCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(84.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = HitaColors.BackgroundSecond
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon background
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(HitaColors.Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = HitaColors.Primary
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text content
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = title,
                    fontSize = 12.sp,
                    color = HitaColors.TextSecondary,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    fontSize = 16.sp,
                    color = HitaColors.TextPrimary,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun EasSection(
    isLoggedIn: Boolean,
    username: String?,
    onLoginClick: () -> Unit,
    onImportClick: () -> Unit,
    onExamClick: () -> Unit,
    onEmptyClassroomClick: () -> Unit,
    onScoresClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Section title
        Text(
            text = "教务系统",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = HitaColors.TextPrimary,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = HitaColors.BackgroundSecond
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // EAS Login Status
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = if (isLoggedIn) {{}} else onLoginClick)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Status dot
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (isLoggedIn) HitaColors.Primary else HitaColors.PrimaryDisabled
                            )
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        text = if (isLoggedIn && username != null) "教务登录为 $username"
                        else "未登录教务",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = HitaColors.TextSecondary,
                        modifier = Modifier.weight(1f)
                    )

                    if (isLoggedIn) {
                        IconButton(onClick = { /* Logout */ }) {
                            Icon(
                                imageVector = Icons.Default.ExitToApp,
                                contentDescription = "退出登录",
                                tint = HitaColors.PrimaryDisabled
                            )
                        }
                    }
                }

                // Function grid
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EasFunctionItem(
                        title = "导入课表",
                        icon = ImageVector.vectorResource(id = com.hita.agent.R.drawable.ic_nav_timetable),
                        onClick = onImportClick,
                        modifier = Modifier.weight(1f)
                    )
                    EasFunctionItem(
                        title = "考试安排",
                        icon = ImageVector.vectorResource(id = com.hita.agent.R.drawable.ic_feature_scores),
                        onClick = onExamClick,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    EasFunctionItem(
                        title = "空教室",
                        icon = ImageVector.vectorResource(id = com.hita.agent.R.drawable.ic_feature_rooms),
                        onClick = onEmptyClassroomClick,
                        modifier = Modifier.weight(1f)
                    )
                    EasFunctionItem(
                        title = "成绩查询",
                        icon = ImageVector.vectorResource(id = com.hita.agent.R.drawable.ic_feature_resources),
                        onClick = onScoresClick,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EasFunctionItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(32.dp),
            tint = HitaColors.Primary
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            fontSize = 16.sp,
            color = HitaColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier.size(24.dp),
            tint = HitaColors.PrimaryDisabled
        )
    }
}

@Composable
private fun CourseResourcesSection(
    onCourseLookupClick: () -> Unit,
    onCourseSubmitClick: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Section title
        Text(
            text = "课程资源",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = HitaColors.TextPrimary,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp)),
            colors = CardDefaults.cardColors(
                containerColor = HitaColors.BackgroundSecond
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column {
                // Course Lookup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCourseLookupClick)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = HitaColors.Primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "课程资料查询",
                            fontSize = 16.sp,
                            color = HitaColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "搜索和浏览课程相关资料",
                            fontSize = 12.sp,
                            color = HitaColors.TextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = HitaColors.PrimaryDisabled
                    )
                }

                // Divider
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(0.5.dp)
                        .background(HitaColors.PrimaryDisabled)
                        .padding(horizontal = 16.dp)
                )

                // Course Submit
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onCourseSubmitClick)
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = HitaColors.Primary
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "提交课程资料",
                            fontSize = 16.sp,
                            color = HitaColors.TextPrimary
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "分享你的课程笔记和资料",
                            fontSize = 12.sp,
                            color = HitaColors.TextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = HitaColors.PrimaryDisabled
                    )
                }
            }
        }
    }
}
