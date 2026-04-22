package com.stupidtree.hitax.ui.main.agent

import android.app.AlertDialog
import android.net.Uri
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

                val fileContent = try {
                    when {
                        fileName.endsWith(".txt", ignoreCase = true) -> {
                            "【文本文件】\n${tempFile.readText(Charsets.UTF_8)}"
                        }
                        fileName.endsWith(".md", ignoreCase = true) -> {
                            "【Markdown文件】\n${tempFile.readText(Charsets.UTF_8)}"
                        }
                        fileName.endsWith(".json", ignoreCase = true) -> {
                            val jsonText = tempFile.readText(Charsets.UTF_8)
                            try {
                                "【JSON文件】\n格式化内容：\n${org.json.JSONObject(jsonText).toString(2)}"
                            } catch (e: Exception) {
                                "【JSON文件】\n$jsonText"
                            }
                        }
                        fileName.endsWith(".xml", ignoreCase = true) -> {
                            "【XML文件】\n${tempFile.readText(Charsets.UTF_8)}"
                        }
                        fileName.endsWith(".html", ignoreCase = true) || fileName.endsWith(".htm", ignoreCase = true) -> {
                            val html = tempFile.readText(Charsets.UTF_8)
                            try {
                                val doc = org.jsoup.Jsoup.parse(html)
                                val title = doc.title()?.takeIf { it.isNotEmpty() } ?: "无标题"
                                val bodyText = doc.body().text()
                                "【HTML网页】\n标题：$title\n\n内容：\n$bodyText"
                            } catch (e: Exception) {
                                "【HTML网页】\n$html"
                            }
                        }
                        fileName.endsWith(".csv", ignoreCase = true) -> {
                            "【CSV表格】\n${tempFile.readText(Charsets.UTF_8)}"
                        }
                        fileName.matches(Regex(".*\\.(jpg|jpeg|png|gif|bmp|webp)$", RegexOption.IGNORE_CASE)) -> {
                            "[图片文件: $fileName（提示：可以描述这张图片的内容）]"
                        }
                        fileName.matches(Regex(".*\\.(mp4|mov|avi|mkv|webm)$", RegexOption.IGNORE_CASE)) -> {
                            "[视频文件: $fileName（提示：可以描述这个视频的内容）]"
                        }
                        fileName.matches(Regex(".*\\.(mp3|wav|ogg|m4a|flac)$", RegexOption.IGNORE_CASE)) -> {
                            "[音频文件: $fileName（提示：可以描述这个音频的内容）]"
                        }
                        fileName.endsWith(".pdf", ignoreCase = true) -> {
                            "[PDF文件: $fileName（提示：请描述PDF文档的主要内容）]"
                        }
                        fileName.endsWith(".doc", ignoreCase = true) || fileName.endsWith(".docx", ignoreCase = true) -> {
                            "[Word文档: $fileName（提示：请描述文档的主要内容）]"
                        }
                        fileName.endsWith(".xls", ignoreCase = true) || fileName.endsWith(".xlsx", ignoreCase = true) -> {
                            "[Excel表格: $fileName（提示：请描述表格的主要数据）]"
                        }
                        fileName.endsWith(".ppt", ignoreCase = true) || fileName.endsWith(".pptx", ignoreCase = true) -> {
                            "[PowerPoint演示文稿: $fileName（提示：请描述演示文稿的主要内容）]"
                        }
                        else -> {
                            null
                        }
                    }
                } catch (e: Exception) {
                    null
                } finally {
                    tempFile.delete()
                }

                activity?.runOnUiThread {
                    viewModel.setLoading(false)
                    if (fileContent != null) {
                        val fullText = "$text\n\n[附件: $fileName]\n$fileContent"
                        doSend(fullText)
                    } else {
                        doSend("$text\n\n[附件: $fileName]\n（提示：该文件类型暂不支持解析，请尝试复制文件内容到对话框）")
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
