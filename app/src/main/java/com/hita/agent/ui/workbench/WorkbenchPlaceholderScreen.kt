package com.hita.agent.ui.workbench

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hita.agent.core.ui.theme.HitaColors

@Composable
fun WorkbenchPlaceholderScreen(
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
                text = "Agent 工作台",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = HitaColors.TextPrimary
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Coming Soon...",
                fontSize = 16.sp,
                color = HitaColors.TextSecondary
            )
        }
    }
}
