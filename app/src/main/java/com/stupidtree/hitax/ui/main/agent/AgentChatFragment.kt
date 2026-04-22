package com.stupidtree.hitax.ui.main.agent

import android.app.AlertDialog
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import com.stupidtree.hitax.agent.core.AgentProvider
import com.stupidtree.hitax.agent.core.AgentSession
import com.stupidtree.hitax.agent.remote.AgentBackendClient
import com.stupidtree.hitax.agent.timetable.TimetableAgentFactory
import com.stupidtree.hitax.agent.timetable.TimetableAgentInput
import com.stupidtree.hitax.agent.timetable.TimetableAgentOutput
import com.stupidtree.hitax.data.model.chat.ChatSession
import com.stupidtree.hitax.databinding.FragmentAgentChatBinding
import com.stupidtree.style.base.BaseFragment
import java.io.File

class AgentChatFragment :
    BaseFragment<AgentChatViewModel, FragmentAgentChatBinding>() {

    override fun getViewModelClass(): Class<AgentChatViewModel> =
        AgentChatViewModel::class.java

    override fun initViewBinding(): FragmentAgentChatBinding =
        FragmentAgentChatBinding.inflate(layoutInflater)

    private val agentProvider: AgentProvider<TimetableAgentInput, TimetableAgentOutput> by lazy {
        TimetableAgentFactory.createProvider()
    }
    private var agentSession: AgentSession<TimetableAgentInput, TimetableAgentOutput>? = null
    private lateinit var messageAdapter: AgentChatMessageAdapter
    private var sessionList: List<ChatSession> = emptyList()

    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setPendingAttachment(it)
        }
    }

    override fun initViews(view: View) {
        viewModel.ensureSession()

        messageAdapter = AgentChatMessageAdapter()
        binding?.messageList?.apply {
            layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
            adapter = messageAdapter
        }

        binding?.sendButton?.setOnClickListener { sendMessage() }
        binding?.attachButton?.setOnClickListener { openFilePicker() }
        binding?.newSessionButton?.setOnClickListener { viewModel.createNewSession() }
        binding?.deleteSessionButton?.setOnClickListener { showDeleteSessionDialog() }

        binding?.inputField?.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessage()
                true
            } else {
                false
            }
        }

        viewModel.messages.observe(viewLifecycleOwner) { list ->
            messageAdapter.submitList(list)
            if (list.isNotEmpty()) {
                binding?.messageList?.scrollToPosition(list.size - 1)
            }
        }
        viewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            sessionList = sessions
            val names = sessions.map { it.title }
            val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_item, names)
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            binding?.sessionSpinner?.adapter = adapter
            val current = viewModel.currentSessionId
            val idx = sessions.indexOfFirst { it.id == current }.coerceAtLeast(0)
            binding?.sessionSpinner?.setSelection(idx, false)
        }
        binding?.sessionSpinner?.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>, v: View?, position: Int, id: Long) {
                sessionList.getOrNull(position)?.let { viewModel.switchToSession(it.id) }
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>) {}
        }
        binding?.sessionSpinner?.setOnLongClickListener {
            val current = sessionList.getOrNull(binding?.sessionSpinner?.selectedItemPosition ?: 0)
            if (current != null) {
                AlertDialog.Builder(requireContext())
                    .setTitle("删除会话")
                    .setMessage("删除「${current.title}」的聊天记录？")
                    .setPositiveButton("删除") { _, _ ->
                        viewModel.deleteSession(current)
                        Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("取消", null)
                    .show()
            }
            true
        }
        viewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            binding?.sendButton?.isEnabled = !loading
            binding?.inputField?.isEnabled = !loading
        }
        viewModel.pendingAttachment.observe(viewLifecycleOwner) { uri ->
            if (uri != null) {
                val fileName = getFileName(uri)
                binding?.attachmentIndicator?.text = "📎 $fileName"
                binding?.attachmentIndicator?.visibility = View.VISIBLE
            } else {
                binding?.attachmentIndicator?.visibility = View.GONE
            }
        }
    }

    private fun openFilePicker() {
        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun getFileName(uri: Uri): String {
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) return it.getString(nameIndex)
            }
        }
        return uri.lastPathSegment ?: "file"
    }

    private fun sendMessage() {
        val text = binding?.inputField?.text?.toString()?.trim() ?: return
        if (text.isEmpty()) return
        if (viewModel.isLoading.value == true) return

        binding?.inputField?.text?.clear()

        val attachmentUri = viewModel.pendingAttachment.value
        viewModel.clearPendingAttachment()

        if (attachmentUri != null) {
            sendWithAttachment(text, attachmentUri)
        } else {
            doSend(text)
        }
    }

    private fun showDeleteSessionDialog() {
        val current = sessionList.getOrNull(binding?.sessionSpinner?.selectedItemPosition ?: 0)
        if (current != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("删除会话")
                .setMessage("删除「${current.title}」的聊天记录？")
                .setPositiveButton("删除") { _, _ ->
                    viewModel.deleteSession(current)
                    Toast.makeText(requireContext(), "已删除", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun sendWithAttachment(text: String, uri: Uri) {
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.TRACE, text = "正在处理附件…"))
        viewModel.setLoading(true)

        Thread {
            try {
                val fileName = getFileName(uri)
                val tempFile = File(requireContext().cacheDir, fileName)
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }

                val fileBytes = tempFile.readBytes()
                val base64Content = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                val mimeType = getMimeType(fileName)

                // 对于小文本文件，直接提取内容
                val fileContent = if (fileBytes.size < 100 * 1024 && isTextFile(fileName)) {
                    "【文本文件内容】\n${tempFile.readText(Charsets.UTF_8)}"
                } else {
                    null
                }

                tempFile.delete()

                activity?.runOnUiThread {
                    viewModel.setLoading(false)
                    if (fileContent != null) {
                        // 小文本文件直接发送内容
                        val fullText = "$text\n\n[附件: $fileName]\n$fileContent"
                        doSend(fullText)
                    } else {
                        // 大文件或二进制文件使用多模态API
                        doSendWithAttachment(text, fileName, base64Content, mimeType)
                    }
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    viewModel.setLoading(false)
                    viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.ASSISTANT, text = "附件处理失败：${e.message}"))
                    doSend(text)
                }
            }
        }.start()
    }

    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = listOf(".txt", ".md", ".json", ".xml", ".csv", ".html", ".htm")
        return textExtensions.any { fileName.endsWith(it, ignoreCase = true) }
    }

    private fun getMimeType(fileName: String): String {
        return when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".doc", ignoreCase = true) -> "application/msword"
            fileName.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.endsWith(".xls", ignoreCase = true) -> "application/vnd.ms-excel"
            fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".ppt", ignoreCase = true) -> "application/vnd.ms-powerpoint"
            fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            fileName.matches(Regex(".*\\.(jpg|jpeg)$", RegexOption.IGNORE_CASE)) -> "image/jpeg"
            fileName.matches(Regex(".*\\.(png)$", RegexOption.IGNORE_CASE)) -> "image/png"
            fileName.matches(Regex(".*\\.(gif)$", RegexOption.IGNORE_CASE)) -> "image/gif"
            fileName.matches(Regex(".*\\.(webp)$", RegexOption.IGNORE_CASE)) -> "image/webp"
            fileName.matches(Regex(".*\\.(mp4)$", RegexOption.IGNORE_CASE)) -> "video/mp4"
            fileName.matches(Regex(".*\\.(mov)$", RegexOption.IGNORE_CASE)) -> "video/quicktime"
            fileName.matches(Regex(".*\\.(mp3)$", RegexOption.IGNORE_CASE)) -> "audio/mpeg"
            else -> "application/octet-stream"
        }
    }

    private fun doSendWithAttachment(userText: String, fileName: String, base64Content: String, mimeType: String) {
        // 发送用户消息，包含文件信息
        val messageWithFile = "$userText\n\n[附件: $fileName]"
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = messageWithFile))

        agentSession?.dispose()
        agentSession = null

        // 使用多模态API发送
        viewModel.sendToLlmWithAttachment(
            text = userText,
            fileName = fileName,
            base64Content = base64Content,
            mimeType = mimeType,
            agentProvider = agentProvider
        )
    }

    private fun doSend(text: String) {
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = text))

        agentSession?.dispose()
        agentSession = null

        viewModel.sendToLlm(
            text = text,
            agentProvider = agentProvider,
        )
    }

    override fun onDestroyView() {
        agentSession?.dispose()
        agentSession = null
        super.onDestroyView()
    }
}
