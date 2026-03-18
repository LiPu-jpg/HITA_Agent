package com.hita.agent.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.hita.agent.core.domain.model.CampusId
import androidx.compose.foundation.shape.RoundedCornerShape

@Composable
fun LoginCard(
    state: LoginUiState,
    onCampusChange: (CampusId) -> Unit,
    onUsernameChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onLoginShenzhen: () -> Unit,
    onMainWebLogin: () -> Unit,
    onWeihaiLogin: () -> Unit,
    onLogout: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "教务账号", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.campusId == CampusId.SHENZHEN) {
                    Button(onClick = { onCampusChange(CampusId.SHENZHEN) }) { Text("深圳") }
                } else {
                    OutlinedButton(onClick = { onCampusChange(CampusId.SHENZHEN) }) { Text("深圳") }
                }
                if (state.campusId == CampusId.MAIN) {
                    Button(onClick = { onCampusChange(CampusId.MAIN) }) { Text("本部") }
                } else {
                    OutlinedButton(onClick = { onCampusChange(CampusId.MAIN) }) { Text("本部") }
                }
                if (state.campusId == CampusId.WEIHAI) {
                    Button(onClick = { onCampusChange(CampusId.WEIHAI) }) { Text("威海") }
                } else {
                    OutlinedButton(onClick = { onCampusChange(CampusId.WEIHAI) }) { Text("威海") }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (state.campusId) {
                CampusId.SHENZHEN -> {
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.username,
                        onValueChange = onUsernameChange,
                        label = { Text("账号") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        modifier = Modifier.fillMaxWidth(),
                        value = state.password,
                        onValueChange = onPasswordChange,
                        label = { Text("密码") },
                        visualTransformation = PasswordVisualTransformation()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onLoginShenzhen,
                        enabled = state.status != LoginStatus.LOGGING_IN
                    ) {
                        Text("登录")
                    }
                }
                CampusId.MAIN -> {
                    Text(
                        text = "本部仅支持 WebView CAS 登录",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onMainWebLogin
                    ) {
                        Text("WebView 登录")
                    }
                }
                CampusId.WEIHAI -> {
                    Text(
                        text = "WebVPN 二维码登录（暂未实现）",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        onClick = onWeihaiLogin
                    ) {
                        Text("WebVPN 二维码登录（暂未实现）")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text(text = statusText(state))
            if (!state.message.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = state.message, style = MaterialTheme.typography.bodySmall)
            }

            if (state.status != LoginStatus.LOGGED_OUT) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onLogout) {
                    Text("退出登录")
                }
            }
        }
    }
}

private fun statusText(state: LoginUiState): String {
    return when (state.status) {
        LoginStatus.LOGGED_OUT -> "未登录"
        LoginStatus.LOGGING_IN -> "正在登录..."
        LoginStatus.LOGGED_IN -> "已登录"
        LoginStatus.STALE -> "会话过期，需重新登录"
    }
}
