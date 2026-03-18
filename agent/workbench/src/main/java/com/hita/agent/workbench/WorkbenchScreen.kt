package com.hita.agent.workbench

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.content.Intent

@Composable
fun WorkbenchScreen(viewModel: WorkbenchViewModel) {
    val state by viewModel.state.collectAsState()
    val contentResolver = LocalContext.current.contentResolver
    val uploadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
        }
        viewModel.onUploadSelected(uri)
    }
    val downloadLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        viewModel.onDownloadTarget(uri?.toString())
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Agent Workbench",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        ActionRow(
            onUpload = {
                uploadLauncher.launch(
                    arrayOf(
                        "text/plain",
                        "text/markdown",
                        "application/pdf",
                        "application/msword",
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                        "application/vnd.ms-powerpoint",
                        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                    )
                )
            },
            onDownload = { downloadLauncher.launch("agent_output.txt") },
            onTools = { viewModel.onToolsClicked() }
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(state.messages) { message ->
                MessageCard(message)
            }
        }

        if (state.busy) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        }

        OutlinedTextField(
            modifier = Modifier.fillMaxWidth(),
            value = state.input,
            onValueChange = viewModel::updateInput,
            label = { Text("Ask the agent") }
        )
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = viewModel::sendQuery,
            enabled = !state.busy
        ) {
            Text("Send")
        }
    }
}

@Composable
private fun ActionRow(
    onUpload: () -> Unit,
    onDownload: () -> Unit,
    onTools: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Button(onClick = onUpload) {
            Text("Upload")
        }
        Button(onClick = onDownload) {
            Text("Download")
        }
        Button(onClick = onTools) {
            Text("Tools")
        }
    }
}

@Composable
private fun MessageCard(message: WorkbenchMessage) {
    val header = when (message.role) {
        WorkbenchRole.USER -> "You"
        WorkbenchRole.ASSISTANT -> "Agent"
        WorkbenchRole.TOOL -> "Tool"
    }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = header, style = MaterialTheme.typography.labelMedium)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message.content, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
