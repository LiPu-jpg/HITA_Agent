package com.hita.agent.ui.workbench

import android.app.Activity
import android.webkit.CookieManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hita.agent.AppContainer
import com.hita.agent.core.domain.model.CampusId
import com.hita.agent.ui.login.LoginCard
import com.hita.agent.ui.login.LoginViewModel
import com.hita.agent.ui.login.MainWebLoginActivity
import com.hita.agent.workbench.WorkbenchScreen
import com.hita.agent.workbench.WorkbenchViewModel

@Composable
fun WorkbenchRoute(container: AppContainer) {
    val context = LocalContext.current
    val loginViewModel: LoginViewModel = viewModel(factory = LoginViewModel.factory(container))
    val loginState by loginViewModel.state.collectAsState()
    val workbenchViewModel: WorkbenchViewModel =
        viewModel(
            factory = WorkbenchViewModel.factory(
                container.agentBackendApi,
                container.easRepository,
                container.plannerConfig,
                container.localRagRepository
            )
        )

    val mainLoginLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val cookies = MainWebLoginActivity.extractCookies(result.data)
            if (cookies.isNotEmpty()) {
                loginViewModel.saveMainSession(cookies)
            }
        }
    }

    LaunchedEffect(loginState.campusId) {
        loginViewModel.refreshStatus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        LoginCard(
            state = loginState,
            onCampusChange = loginViewModel::setCampus,
            onUsernameChange = loginViewModel::updateUsername,
            onPasswordChange = loginViewModel::updatePassword,
            onLoginShenzhen = loginViewModel::loginShenzhen,
            onMainWebLogin = {
                mainLoginLauncher.launch(MainWebLoginActivity.createIntent(context))
            },
            onWeihaiLogin = {
                Toast.makeText(context, "暂未支持", Toast.LENGTH_SHORT).show()
            },
            onLogout = {
                if (loginState.campusId == CampusId.MAIN) {
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                }
                loginViewModel.logout()
            }
        )
        Spacer(modifier = Modifier.height(12.dp))
        WorkbenchScreen(viewModel = workbenchViewModel)
    }
}
