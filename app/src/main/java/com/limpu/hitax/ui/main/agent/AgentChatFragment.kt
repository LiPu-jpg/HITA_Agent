package com.limpu.hitax.ui.main.agent

import android.app.AlertDialog
import android.net.Uri
import android.util.Base64
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.fragment.app.viewModels
import com.limpu.hitax.BuildConfig
import com.limpu.hitax.agent.core.AgentProvider
import com.limpu.hitax.utils.LogUtils
import com.limpu.hitax.agent.core.AgentSession
import com.limpu.hitax.agent.remote.AgentBackendClient
import com.limpu.hitax.agent.timetable.TimetableAgentFactory
import com.limpu.hitax.agent.timetable.TimetableAgentInput
import com.limpu.hitax.agent.timetable.TimetableAgentOutput
import com.limpu.hitax.data.model.chat.ChatSession
import com.limpu.hitax.databinding.FragmentAgentChatBinding
import com.limpu.hitax.ui.base.HiltBaseFragment
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

@AndroidEntryPoint
class AgentChatFragment : HiltBaseFragment<FragmentAgentChatBinding>() {

    protected val viewModel: AgentChatViewModel by viewModels()

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

            // 只在键盘弹出/收起时自动滚动，不影响用户手动滚动
            var previousKeyboardHeight = 0
            addOnLayoutChangeListener { _, _, top, bottom, _, _, oldTop, _, oldBottom ->
                val viewHeight = bottom - top
                val oldViewHeight = oldBottom - oldTop
                val heightDiff = viewHeight - oldViewHeight

                // 只有在视图高度变化超过100px时，才认为是键盘弹出/收起
                // 避免用户手动滚动时触发自动滚动
                if (kotlin.math.abs(heightDiff) > 100) {
                    val keyboardVisible = heightDiff < 0  // 高度减少 = 键盘弹出

                    if (keyboardVisible && previousKeyboardHeight == 0) {
                        // 键盘刚刚弹出，滚动到底部
                        post {
                            if (adapter?.itemCount ?: 0 > 0) {
                                scrollToPosition((adapter?.itemCount ?: 1) - 1)
                            }
                        }
                        previousKeyboardHeight = -heightDiff
                    } else if (!keyboardVisible && previousKeyboardHeight > 0) {
                        // 键盘刚刚收起
                        previousKeyboardHeight = 0
                    }
                }
            }
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

    private fun getFileName(uri: Uri): String {
        // 优先从 URI path 中提取真实文件名（避免显示名称被应用修改）
        val path = uri.path
        if (path != null && path.contains("/")) {
            val nameFromPath = path.substringAfterLast("/")
            // 如果路径中的文件名包含常见后缀，优先使用
            if (nameFromPath.contains(".") && !nameFromPath.endsWith(".mht")) {
                return nameFromPath
            }
        }

        // 其次尝试从 ContentResolver 查询显示名称
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    val displayName = it.getString(nameIndex)
                    // 如果显示名称是 .mht 但包含 ".pdf"，尝试提取真实名称
                    if (displayName.endsWith(".mht", ignoreCase = true) && displayName.contains(".pdf", ignoreCase = true)) {
                        val pdfIndex = displayName.indexOf(".pdf", ignoreCase = true)
                        if (pdfIndex > 0) {
                            val realName = displayName.substring(0, pdfIndex + 4)
                            return realName
                        }
                    }
                    return displayName
                }
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

    // 文件大小和数量限制
    private val MAX_FILE_SIZE = 20 * 1024 * 1024 // 20MB
    private val MAX_IMAGE_SIZE = 10 * 1024 * 1024 // 10MB (图片/视频)
    private val MAX_ATTACHMENTS_PER_MESSAGE = 3 // 每次对话最多附件数

    private fun sendWithAttachment(text: String, uri: Uri) {
        // 先清空输入框，阻塞发送
        binding?.inputField?.text?.clear()
        viewModel.setLoading(true)

        Thread {
            try {

                val fileName = getFileName(uri)

                // 检查文件大小
                val inputStream = requireContext().contentResolver.openInputStream(uri)
                if (inputStream == null) {
                    LogUtils.e( "❌ 无法打开输入流")
                    activity?.runOnUiThread {
                        viewModel.setLoading(false)
                        binding?.inputField?.text?.append(text)
                        viewModel.addMessage(AgentChatMessage(
                            role = AgentChatMessage.Role.ASSISTANT,
                            text = "无法打开文件，请重试"
                        ))
                    }
                    return@Thread
                }

                val fileSize = inputStream.use { it.available() }

                val maxSize = when {
                    fileName.endsWith(".jpg", ignoreCase = true) ||
                    fileName.endsWith(".jpeg", ignoreCase = true) ||
                    fileName.endsWith(".png", ignoreCase = true) ||
                    fileName.endsWith(".gif", ignoreCase = true) ||
                    fileName.endsWith(".bmp", ignoreCase = true) ||
                    fileName.endsWith(".webp", ignoreCase = true) -> {
                        MAX_IMAGE_SIZE
                    }
                    fileName.endsWith(".mp4", ignoreCase = true) ||
                    fileName.endsWith(".mov", ignoreCase = true) ||
                    fileName.endsWith(".avi", ignoreCase = true) ||
                    fileName.endsWith(".mkv", ignoreCase = true) ||
                    fileName.endsWith(".webm", ignoreCase = true) -> {
                        MAX_IMAGE_SIZE
                    }
                    else -> {
                        MAX_FILE_SIZE
                    }
                }

                if (fileSize > maxSize) {
                    LogUtils.e( "❌ 文件过大: ${formatFileSize(fileSize)} > ${formatFileSize(maxSize)}")
                    activity?.runOnUiThread {
                        viewModel.setLoading(false)
                        binding?.inputField?.text?.append(text)  // 恢复用户输入
                        viewModel.addMessage(AgentChatMessage(
                            role = AgentChatMessage.Role.ASSISTANT,
                            text = "文件过大！\n当前文件：${fileName} (${formatFileSize(fileSize)})\n限制：${formatFileSize(maxSize)}\n\n建议：\n- 图片/视频请压缩到10MB以下\n- 文档请控制在20MB以下"
                        ))
                    }
                    return@Thread
                }

                // 判断文件类型，不符合条件直接返回
                val mimeType = getMimeType(fileName)

                val needLocalParse = when {
                    fileSize < 100 * 1024 && isTextFile(fileName) -> {
                        true
                    }
                    fileName.endsWith(".docx", ignoreCase = true) -> {
                        true
                    }
                    fileName.endsWith(".xlsx", ignoreCase = true) -> {
                        true
                    }
                    fileName.endsWith(".pptx", ignoreCase = true) -> {
                        true
                    }
                    fileName.endsWith(".pdf", ignoreCase = true) -> {
                        true
                    }
                    else -> {
                        false
                    }
                }

                val needCloudAI = mimeType.startsWith("image/") || mimeType.startsWith("video/")

                if (!needLocalParse && !needCloudAI) {
                    LogUtils.e( "❌ 不支持的文件类型: $fileName")
                    activity?.runOnUiThread {
                        viewModel.setLoading(false)
                        binding?.inputField?.text?.append(text)  // 恢复用户输入
                        viewModel.addMessage(AgentChatMessage(
                            role = AgentChatMessage.Role.ASSISTANT,
                            text = "不支持的文件类型：${fileName}\n\n支持的格式：\n- 文档：Word、Excel、PowerPoint（本地解析）\n- 文档：PDF（云端AI解析）\n- 图片：JPG、PNG、GIF、WebP\n- 视频：MP4、MOV"
                        ))
                    }
                    return@Thread
                }


                val cacheDir = requireContext().cacheDir

                val tempFile = File(cacheDir, fileName)

                @Suppress("UNUSED_VARIABLE") val copyStart = System.currentTimeMillis()
                var bytesCopied = 0L
                requireContext().contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } > 0) {
                            output.write(buffer, 0, bytesRead)
                            bytesCopied += bytesRead
                        }
                    }
                }
                @Suppress("UNUSED_VARIABLE") val copyEnd = System.currentTimeMillis()

                // 智能分流策略

                val parseStart = System.currentTimeMillis()
                val result = when {
                    // 1. 小文本文件 - 本地直接读取
                    fileSize < 100 * 1024 && isTextFile(fileName) -> {
                        val fileText = tempFile.readText(Charsets.UTF_8)
                        LogUtils.d( "✅ 文本读取完成，长度: ${fileText.length}")
                        LocalParseResult.Success("【文本文件】\n$fileText")
                    }

                    // 2. PDF/Office文档 - 本地解析
                    fileName.endsWith(".pdf", ignoreCase = true) -> {
                        LogUtils.d( "📄 解析方式: PDF文档")
                        parsePdfFile(tempFile)
                    }
                    fileName.endsWith(".docx", ignoreCase = true) -> {
                        parseDocxFile(tempFile)
                    }
                    fileName.endsWith(".xlsx", ignoreCase = true) -> {
                        parseExcelFile(tempFile)
                    }
                    fileName.endsWith(".pptx", ignoreCase = true) -> {
                        parsePptxFile(tempFile)
                    }

                    // 3. 图片/视频 - 使用智谱多模态
                    mimeType.startsWith("image/") || mimeType.startsWith("video/") -> {
                        null
                    }

                    // 4. 其他不支持的类型
                    else -> {
                        LogUtils.e( "❌ 不支持的文件类型")
                        LocalParseResult.Error("不支持的文件类型")
                    }
                }
                val parseEnd = System.currentTimeMillis()
                LogUtils.d( "⏱️ 解析耗时: ${parseEnd - parseStart}ms")

                // 只在需要云端AI时才读取文件并编码
                val base64Content = if (result == null && needCloudAI) {
                    try {
                        LogUtils.d( "========== 开始图片/视频处理 ==========")
                        LogUtils.d( "📁 文件路径: ${tempFile.absolutePath}")
                        LogUtils.d( "📏 文件大小: ${tempFile.length()} bytes (${tempFile.length() / 1024}KB)")

                        // 读取文件字节
                        LogUtils.d( "📖 开始读取文件到内存...")
                        val fileBytes = tempFile.readBytes()
                        LogUtils.d( "✅ 文件读取完成: ${fileBytes.size} bytes")
                        LogUtils.d( "🔢 前32字节(hex): ${fileBytes.take(32).joinToString(" ") { "%02X".format(it) }}")
                        LogUtils.d( "🔢 前32字节(char): ${fileBytes.take(32).joinToString("") { if(it in 32..126) it.toChar().toString() else "." }}")

                        // Base64编码
                        LogUtils.d( "🔄 开始Base64编码...")
                        val startTime = System.currentTimeMillis()
                        val base64Encoded = Base64.encodeToString(fileBytes, Base64.NO_WRAP)
                        val endTime = System.currentTimeMillis()
                        LogUtils.d( "✅ Base64编码完成")
                        LogUtils.d( "⏱️ 编码耗时: ${endTime - startTime}ms")
                        LogUtils.d( "📊 Base64长度: ${base64Encoded.length} 字符")
                        LogUtils.d( "📊 Base64前128字符: ${base64Encoded.take(128)}...")
                        LogUtils.d( "📊 Base64后32字符: ...${base64Encoded.takeLast(32)}")

                        // 文件头检测（验证图片格式）
                        LogUtils.d( "🔍 文件头检测:")
                        when {
                            fileBytes.size >= 4 && fileBytes[0].toInt() == 0xFF && fileBytes[1].toInt() == 0xD8 && fileBytes[2].toInt() == 0xFF -> {
                                LogUtils.d( "   ✅ 检测到JPEG格式")
                            }
                            fileBytes.size >= 8 && String(fileBytes.take(8).toByteArray()) == "\u0089PNG\r\n\u001A\n" -> {
                                LogUtils.d( "   ✅ 检测到PNG格式")
                            }
                            fileBytes.size >= 4 && String(fileBytes.take(4).toByteArray()) == "RIFF" -> {
                                LogUtils.d( "   ✅ 检测到WEBP格式")
                            }
                            fileBytes.size >= 4 && fileBytes[0].toInt() == 0x47 && fileBytes[1].toInt() == 0x49 && fileBytes[2].toInt() == 0x46 -> {
                                LogUtils.d( "   ✅ 检测到GIF格式")
                            }
                            fileBytes.size >= 12 && String(fileBytes.take(4).toByteArray()) == "\u0000\u0000\u0000\u0014" && String(fileBytes.take(4).toByteArray()) == "ftypmp42" -> {
                                LogUtils.d( "   ✅ 检测到MP4格式")
                            }
                            else -> {
                                LogUtils.w( "   ⚠️ 未知文件格式")
                                LogUtils.d( "   文件头(hex): ${fileBytes.take(16).joinToString(" ") { "%02X".format(it) }}")
                            }
                        }

                        LogUtils.d( "========== 图片/视频处理完成 ==========")
                        base64Encoded
                    } catch (e: OutOfMemoryError) {
                        LogUtils.e( "❌ Base64编码内存不足", e)
                        LogUtils.e( "可用内存: ${android.app.ActivityManager.MemoryInfo()}")
                        activity?.runOnUiThread {
                            viewModel.setLoading(false)
                            binding?.inputField?.text?.append(text)  // 恢复用户输入
                            viewModel.addMessage(AgentChatMessage(
                                role = AgentChatMessage.Role.ASSISTANT,
                                text = "文件过大，无法处理。\n\n建议：\n1. 图片请压缩后重新上传（建议小于5MB）\n2. 视频请剪辑后重新上传（建议小于10MB）\n3. 或使用截图功能"
                            ))
                        }
                        return@Thread
                    }
                } else {
                    null
                }

                tempFile.delete()


                activity?.runOnUiThread {
                    when (result) {
                        is LocalParseResult.Success -> {
                            // 本地解析成功，限制文本长度后发送

                            val maxLength = 5000  // 限制发送给AI的文本长度
                            val content = if (result.content.length > maxLength) {
                                result.content.take(maxLength) + "\n\n...(内容过长，仅显示前${maxLength}字)"
                            } else {
                                result.content
                            }
                            val fullText = "$text\n\n[附件: $fileName]\n$content"
                            doSend(fullText)
                        }
                        is LocalParseResult.Error -> {
                            // 本地解析失败，降级到智谱（仅图片/视频）
                            LogUtils.e( "❌ 本地解析失败: ${result.error}")
                            LogUtils.e( "📋 错误详情: ${result.error}")

                            if (needCloudAI && base64Content != null) {
                                LogUtils.d( "📤 降级到云端AI处理")
                                doSendWithAttachment(text, fileName, base64Content, mimeType)
                            } else {
                                LogUtils.e( "❌ 无法降级，显示错误消息")
                                viewModel.setLoading(false)
                                binding?.inputField?.text?.append(text)  // 恢复用户输入
                                viewModel.addMessage(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = "附件解析失败：${result.error}\n\n建议：请复制文件内容粘贴到对话框中"
                                ))
                            }
                        }
                        null -> {
                            // 需要使用智谱多模态（图片/视频）
                            LogUtils.d( "📤 使用智谱多模态AI处理")
                            LogUtils.d( "📦 Base64编码长度: ${base64Content?.length ?: 0}")

                            if (base64Content != null) {
                                LogUtils.d( "✅ Base64内容有效，调用多模态API")
                                doSendWithAttachment(text, fileName, base64Content, mimeType)
                            } else {
                                LogUtils.e( "❌ Base64内容为空")
                                viewModel.setLoading(false)
                                binding?.inputField?.text?.append(text)  // 恢复用户输入
                                viewModel.addMessage(AgentChatMessage(
                                    role = AgentChatMessage.Role.ASSISTANT,
                                    text = "不支持的文件类型，请尝试图片或视频"
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                LogUtils.e( "❌ 附件处理异常", e)
                LogUtils.e( "❌ 异常类型: ${e::class.simpleName}")
                LogUtils.e( "❌ 异常消息: ${e.message}")
                LogUtils.e( "❌ 堆栈跟踪: ${e.stackTraceToString()}")
                activity?.runOnUiThread {
                    viewModel.setLoading(false)
                    binding?.inputField?.text?.append(text)  // 恢复用户输入
                    viewModel.addMessage(AgentChatMessage(
                        role = AgentChatMessage.Role.ASSISTANT,
                        text = "附件处理失败：${e.message}"
                    ))
                }
            } catch (e: OutOfMemoryError) {
                LogUtils.e( "❌ 内存不足异常", e)
                LogUtils.e( "❌ 可用内存信息: ${android.app.ActivityManager.MemoryInfo()}")
                activity?.runOnUiThread {
                    viewModel.setLoading(false)
                    binding?.inputField?.text?.append(text)  // 恢复用户输入
                    viewModel.addMessage(AgentChatMessage(
                        role = AgentChatMessage.Role.ASSISTANT,
                        text = "内存不足，请尝试更小的文件"
                    ))
                }
            }
        }.start()
    }

    private fun formatFileSize(bytes: Int): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }

    private fun openFilePicker() {
        // 检查是否已有待发送的附件
        if (viewModel.pendingAttachment.value != null) {
            AlertDialog.Builder(requireContext())
                .setTitle("附件提示")
                .setMessage("您已经添加了一个附件，请先发送当前消息后再添加新附件。\n\n每条消息最多支持 ${MAX_ATTACHMENTS_PER_MESSAGE} 个附件。")
                .setPositiveButton("知道了", null)
                .show()
            return
        }

        filePickerLauncher.launch(arrayOf("*/*"))
    }

    private fun isTextFile(fileName: String): Boolean {
        val textExtensions = listOf(".txt", ".md", ".json", ".xml", ".csv", ".html", ".htm")
        return textExtensions.any { fileName.endsWith(it, ignoreCase = true) }
    }

    private fun getMimeType(fileName: String): String {
        LogUtils.d( "判断MIME类型，文件名: $fileName")

        // 统一使用 endsWith 方法，更可靠
        val mimeTypeFromFile = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".docx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            fileName.endsWith(".doc", ignoreCase = true) && !fileName.endsWith(".docx", ignoreCase = true) -> "application/msword"
            fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            fileName.endsWith(".xls", ignoreCase = true) && !fileName.endsWith(".xlsx", ignoreCase = true) -> "application/vnd.ms-excel"
            fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            fileName.endsWith(".ppt", ignoreCase = true) && !fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.ms-powerpoint"
            fileName.endsWith(".jpg", ignoreCase = true) || fileName.endsWith(".jpeg", ignoreCase = true) -> "image/jpeg"
            fileName.endsWith(".png", ignoreCase = true) -> "image/png"
            fileName.endsWith(".gif", ignoreCase = true) -> "image/gif"
            fileName.endsWith(".webp", ignoreCase = true) -> "image/webp"
            fileName.endsWith(".mp4", ignoreCase = true) -> "video/mp4"
            fileName.endsWith(".mov", ignoreCase = true) -> "video/quicktime"
            fileName.endsWith(".mp3", ignoreCase = true) -> "audio/mpeg"
            else -> null
        }

        if (mimeTypeFromFile != null) {
            LogUtils.d( "从文件名识别MIME: $mimeTypeFromFile")
            return mimeTypeFromFile
        }

        LogUtils.d( "无法从文件名识别，使用默认类型: application/octet-stream")
        return "application/octet-stream"
    }

    private sealed class LocalParseResult {
        data class Success(val content: String) : LocalParseResult()
        data class Error(val error: String) : LocalParseResult()
    }

    private fun parsePdfFile(file: File): LocalParseResult {
        var parser: com.tom_roush.pdfbox.pdmodel.PDDocument? = null
        return try {
            LogUtils.d( "文件大小: ${file.length() / 1024}KB")

            parser = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val totalPages = parser.numberOfPages
            LogUtils.d( "总页数: $totalPages")

            // 限制页数，避免解析超大PDF导致内存问题
            val maxPages = 10
            val pagesToParse = minOf(totalPages, maxPages)
            LogUtils.d( "将解析前 $pagesToParse 页")

            val text = StringBuilder()
            text.append("【PDF文档】\n")
            if (totalPages > maxPages) {
                text.append("总页数：${totalPages}（仅解析前${maxPages}页）\n\n内容：\n")
            } else {
                text.append("页数：${totalPages}\n\n内容：\n")
            }

            // 使用更宽松的文本提取模式
            LogUtils.d( "开始提取文本...")
            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            stripper.startPage = 1
            stripper.endPage = pagesToParse
            stripper.setSortByPosition(true)

            val pdfText = stripper.getText(parser)
            LogUtils.d( "✅ 提取完成，文本长度: ${pdfText.length}")

            // 限制文本长度
            val maxLength = 5000
            if (pdfText.length > maxLength) {
                LogUtils.d( "⚠️ 文本过长，截取到 $maxLength 字")
                text.append(pdfText.take(maxLength))
                text.append("\n\n...(内容过长，仅显示前${maxLength}字)")
            } else {
                text.append(pdfText)
            }

            parser.close()
            LocalParseResult.Success(text.toString())
        } catch (e: OutOfMemoryError) {
            LogUtils.e( "❌ 内存不足", e)
            parser?.close()
            LocalParseResult.Error("内存不足，请尝试更小的PDF文件")
        } catch (e: Exception) {
            LogUtils.e( "❌ PDF解析失败: ${e.message}", e)
            parser?.close()
            LocalParseResult.Error("错误：${e.message}\n\n建议：截图上传或复制PDF中的文本")
        }
    }

    private fun loadCMapResources() {
        LogUtils.d( "[PDF-CMap] 📚 开始加载 CMap 资源...")
        val cmapDir = "com/tom_roush/pdfbox/resources/cmap/"

        val assets = context?.assets
        if (assets == null) {
            LogUtils.e( "[PDF-CMap] ❌ Assets 为 null，无法加载 CMap")
            return
        }

        val cmapFiles = assets.list(cmapDir)
        if (cmapFiles == null) {
            LogUtils.e( "[PDF-CMap] ❌ 无法列出 CMap 目录")
            return
        }

        LogUtils.d( "[PDF-CMap] 📂 找到 ${cmapFiles.size} 个 CMap 文件")

        try {
            val cMapManagerClass = Class.forName("com.tom_roush.pdfbox.pdmodel.font.CMapManager")
            LogUtils.d( "[PDF-CMap] ✅ 获取 CMapManager 类")

            val cMapParserClass = Class.forName("com.tom_roush.fontbox.cmap.CMapParser")
            LogUtils.d( "[PDF-CMap] ✅ 获取 CMapParser 类")

            // 尝试访问 CMapManager 的预定义 CMap 缓存
            // 查找所有可能的字段名
            val possibleFieldNames = listOf(
                "PREDEFINED_CMAPS",
                "predefinedCMaps",
                "cmapCache",
                "CMAP_CACHE"
            )

            var cacheField: java.lang.reflect.Field? = null
            for (fieldName in possibleFieldNames) {
                try {
                    cacheField = cMapManagerClass.getDeclaredField(fieldName)
                    LogUtils.d( "[PDF-CMap] 🔍 找到字段: $fieldName")
                    break
                } catch (e: NoSuchFieldException) {
                    // 继续尝试下一个字段名
                }
            }

            if (cacheField != null) {
                cacheField.isAccessible = true
                val cache = cacheField.get(null)
                LogUtils.d( "[PDF-CMap] ✅ 缓存字段类型: ${cache?.javaClass?.name}")
                LogUtils.d( "[PDF-CMap] ✅ 缓存字段值: $cache")
            } else {
                LogUtils.w( "[PDF-CMap] ⚠️ 未找到缓存字段")
            }

            // 尝试直接解析 CMap 并注册
            val parseMethod = cMapParserClass.getDeclaredMethod("parse", java.io.InputStream::class.java)
            parseMethod.isAccessible = true
            LogUtils.d( "[PDF-CMap] ✅ 找到 parse 方法")

            // 尝试创建 CMap 对象并注册
            var registeredCount = 0
            for (cmapFile in cmapFiles) {
                try {
                    // 只注册关键的 CMap
                    if (cmapFile !in listOf("Identity-H", "Identity-V", "Adobe-GB1-UCS2", "Adobe-CNS1-UCS2", "Adobe-Japan1-UCS2", "Adobe-Korea1-UCS2")) {
                        continue
                    }

                    LogUtils.d( "[PDF-CMap] 📖 尝试注册: $cmapFile")
                    val inputStream = assets.open("$cmapDir$cmapFile")
                    val cmap = parseMethod.invoke(null, inputStream)
                    LogUtils.d( "[PDF-CMap] ✅ 解析成功: $cmapFile -> $cmap")
                    inputStream.close()
                    registeredCount++
                } catch (e: Exception) {
                    LogUtils.w( "[PDF-CMap] ⚠️ 注册失败 $cmapFile: ${e::class.simpleName} - ${e.message}")
                }
            }

            LogUtils.d( "[PDF-CMap] 📊 注册统计: 成功 $registeredCount")

            LogUtils.d( "[PDF-CMap] ✅ CMap 资源加载完成")
        } catch (e: Exception) {
            LogUtils.e( "[PDF-CMap] ❌ 加载 CMap 资源时出错: ${e::class.simpleName} - ${e.message}")
            e.printStackTrace()
        }
    }

    private fun parseDocxFile(file: File): LocalParseResult {
        return try {

            if (!file.exists()) {
                LogUtils.e( "❌ 文件不存在！")
                return LocalParseResult.Error("文件不存在，请重试")
            }

            // 直接使用 File 对象，不通过 Uri
            val text = extractDocxTextDirectly(file)
            LogUtils.d( "📝 文本预览: ${text.take(200)}")

            LocalParseResult.Success("【Word文档】\n$text")
        } catch (e: OutOfMemoryError) {
            LogUtils.e( "❌ 内存不足", e)
            LocalParseResult.Error("内存不足，请尝试更小的文件")
        } catch (e: Exception) {
            LogUtils.e( "❌ 解析失败: ${e.message}", e)
            LocalParseResult.Error("错误：${e.message}\n\n建议：复制文档中的文本粘贴到聊天框")
        }
    }

    /**
     * 直接从 File 对象提取 DOCX 文本
     */
    private fun extractDocxTextDirectly(file: File): String {
        val result = StringBuilder()
        val MAX_LENGTH = 5000


        java.util.zip.ZipInputStream(file.inputStream()).use { zip ->
            var entry = zip.nextEntry
            var entryCount = 0

            while (entry != null) {
                entryCount++

                if (entry.name == "word/document.xml") {

                    // 读取 XML 内容
                    val xmlContent = zip.reader().readText()

                    // 提取 <w:t> 标签内的文本
                    val textPattern = Regex("<w:t[^>]*>(.*?)</w:t>", RegexOption.DOT_MATCHES_ALL)
                    val matches = textPattern.findAll(xmlContent).toList()


                    var inParagraph = false
                    for (match in matches) {
                        val text = match.groupValues[1]
                            .replace("&lt;", "<")
                            .replace("&gt;", ">")
                            .replace("&amp;", "&")
                            .replace("&quot;", "\"")
                            .replace("&apos;", "'")
                            .trim()

                        if (text.isNotEmpty()) {
                            if (!inParagraph) {
                                if (result.isNotEmpty()) result.append("\n")
                                inParagraph = true
                            }
                            result.append(text).append(" ")

                            // 限制长度
                            if (result.length > MAX_LENGTH) {
                                break
                            }
                        }
                    }

                    break
                }
                entry = zip.nextEntry
            }

            if (entryCount == 0) {
                LogUtils.e( "❌ ZIP文件为空")
            }
        }

        // 后处理文本
        val processed = result.toString()
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\n\\s*\\n"), "\n")
            .trim()

        return if (processed.length > MAX_LENGTH) {
            processed.take(MAX_LENGTH) + "\n\n...(内容过长，仅显示前${MAX_LENGTH}字)"
        } else {
            processed
        }
    }

    private fun parseExcelFile(file: File): LocalParseResult {
        return try {
            val fis = java.io.FileInputStream(file)
            val workbook = org.apache.poi.xssf.usermodel.XSSFWorkbook(fis)
            val text = StringBuilder()
            text.append("【Excel表格】\n工作表数量：${workbook.numberOfSheets}\n\n")

            val sheet = workbook.getSheetAt(0)
            text.append("工作表1：${sheet.sheetName}\n")

            var rowCount = 0
            val maxRows = 100
            val maxCols = 20

            for (row in sheet) {
                if (rowCount >= maxRows) {
                    text.append("\n...(行数过多，仅显示前${maxRows}行)")
                    break
                }

                val rowData = StringBuilder()
                var colCount = 0

                for (cell in row) {
                    if (colCount >= maxCols) {
                        rowData.append(" ...(列数过多)")
                        break
                    }

                    val cellValue = when (cell.cellTypeEnum) {
                        org.apache.poi.ss.usermodel.CellType.STRING -> cell.stringCellValue
                        org.apache.poi.ss.usermodel.CellType.NUMERIC -> cell.numericCellValue.toString()
                        org.apache.poi.ss.usermodel.CellType.BOOLEAN -> cell.booleanCellValue.toString()
                        org.apache.poi.ss.usermodel.CellType.FORMULA -> cell.cellFormula
                        else -> ""
                    }

                    if (cellValue.isNotEmpty()) {
                        rowData.append("[$cellValue] ")
                    }
                    colCount++
                }

                if (rowData.isNotEmpty()) {
                    text.append("第${rowCount + 1}行：$rowData\n")
                }
                rowCount++
            }

            workbook.close()
            fis.close()
            LocalParseResult.Success(text.toString())
        } catch (e: OutOfMemoryError) {
            LogUtils.e( "❌ 内存不足", e)
            LocalParseResult.Error("内存不足，请尝试更小的文件")
        } catch (e: Exception) {
            LogUtils.e( "❌ 解析失败: ${e.message}", e)
            LocalParseResult.Error("错误：${e.message}")
        }
    }

    private fun parsePptxFile(file: File): LocalParseResult {
        return try {
            val fis = java.io.FileInputStream(file)
            val slideShow = org.apache.poi.xslf.usermodel.XMLSlideShow(fis)
            val text = StringBuilder()
            text.append("【PowerPoint演示文稿】\n幻灯片数量：${slideShow.slides.size}\n\n")

            var slideCount = 0
            val maxLength = 5000
            var charCount = 0

            for (slide in slideShow.slides) {
                slideCount++
                text.append("幻灯片${slideCount}：\n")

                val slideText = StringBuilder()
                for (shape in slide.shapes) {
                    if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                        val shapeText = shape.text
                        if (shapeText.isNotEmpty()) {
                            if (charCount + shapeText.length > maxLength) {
                                slideText.append(shapeText.take(maxLength - charCount))
                                charCount = maxLength
                                break
                            }
                            slideText.append(shapeText).append("\n")
                            charCount += shapeText.length
                        }
                    }
                }

                if (slideText.isNotEmpty()) {
                    text.append(slideText.toString())
                    text.append("\n")
                }

                if (charCount >= maxLength) {
                    text.append("\n...(内容过长，仅显示前${maxLength}字符)")
                    break
                }
            }

            slideShow.close()
            fis.close()
            LocalParseResult.Success(text.toString())
        } catch (e: OutOfMemoryError) {
            LogUtils.e( "❌ 内存不足", e)
            LocalParseResult.Error("内存不足，请尝试更小的文件")
        } catch (e: Exception) {
            LogUtils.e( "❌ 解析失败: ${e.message}", e)
            LocalParseResult.Error("错误：${e.message}")
        }
    }

    private fun doSendWithAttachment(userText: String, fileName: String, base64Content: String, mimeType: String) {
        LogUtils.d( "========== 发送到多模态API ==========")
        LogUtils.d( "📝 用户文本: ${userText.take(100)}${if(userText.length>100)"..." else ""}")
        LogUtils.d( "📁 文件名: $fileName")
        LogUtils.d( "📋 MIME类型: $mimeType")
        LogUtils.d( "📊 Base64长度: ${base64Content.length}")
        LogUtils.d( "🔧 AgentProvider: ${agentProvider::class.simpleName}")

        // 发送用户消息，包含文件信息
        val messageWithFile = "$userText\n\n[附件: $fileName]"
        LogUtils.d( "💬 显示消息: $messageWithFile")
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = messageWithFile))

        agentSession?.dispose()
        agentSession = null

        LogUtils.d( "🚀 调用 sendToLlmWithAttachment...")
        // 使用多模态API发送（仅图片/视频）
        viewModel.sendToLlmWithAttachment(
            text = userText,
            fileName = fileName,
            base64Content = base64Content,
            mimeType = mimeType,
            agentProvider = agentProvider
        )
        LogUtils.d( "========== 发送完成 ==========")
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

    /**
     * 生成debug版本的详细错误信息
     * 仅在debug build中显示堆栈跟踪和技术细节
     */

    override fun onStart() {
        super.onStart()
    }

    override fun onDestroyView() {
        agentSession?.dispose()
        agentSession = null
        super.onDestroyView()
    }
}
