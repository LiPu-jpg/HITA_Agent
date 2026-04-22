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

                // 智能分流策略
                val result = when {
                    // 1. 小文本文件 - 本地直接读取
                    fileBytes.size < 100 * 1024 && isTextFile(fileName) -> {
                        LocalParseResult.Success("【文本文件】\n${tempFile.readText(Charsets.UTF_8)}")
                    }

                    // 2. PDF/Office文档 - 本地解析
                    fileName.endsWith(".pdf", ignoreCase = true) -> parsePdfFile(tempFile)
                    fileName.endsWith(".docx", ignoreCase = true) -> parseDocxFile(tempFile)
                    fileName.endsWith(".xlsx", ignoreCase = true) -> parseExcelFile(tempFile)
                    fileName.endsWith(".pptx", ignoreCase = true) -> parsePptxFile(tempFile)

                    // 3. 图片/视频 - 需要智谱多模态理解
                    mimeType.startsWith("image/") || mimeType.startsWith("video/") -> {
                        null  // 标记需要使用智谱
                    }

                    // 4. 其他文件类型
                    else -> null
                }

                tempFile.delete()

                activity?.runOnUiThread {
                    when (result) {
                        is LocalParseResult.Success -> {
                            // 本地解析成功，直接发送内容
                            viewModel.setLoading(false)
                            val fullText = "$text\n\n[附件: $fileName]\n${result.content}"
                            doSend(fullText)
                        }
                        is LocalParseResult.Error -> {
                            // 本地解析失败，降级到智谱
                            doSendWithAttachment(text, fileName, base64Content, mimeType)
                        }
                        null -> {
                            // 需要使用智谱多模态（图片/视频）
                            doSendWithAttachment(text, fileName, base64Content, mimeType)
                        }
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

    private sealed class LocalParseResult {
        data class Success(val content: String) : LocalParseResult()
        data class Error(val error: String) : LocalParseResult()
    }

    private fun parsePdfFile(file: File): LocalParseResult {
        return try {
            val parser = com.tom_roush.pdfbox.pdmodel.PDDocument.load(file)
            val text = StringBuilder()
            text.append("【PDF文档】\n页数：${parser.numberOfPages}\n\n内容：\n")

            val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
            val pdfText = stripper.getText(parser)

            // 限制文本长度，避免太长
            val maxLength = 10000
            if (pdfText.length > maxLength) {
                text.append(pdfText.take(maxLength))
                text.append("\n\n...(内容过长，仅显示前${maxLength}字符)")
            } else {
                text.append(pdfText)
            }

            parser.close()
            LocalParseResult.Success(text.toString())
        } catch (e: Exception) {
            LocalParseResult.Error("【PDF解析失败】\n错误：${e.message}")
        }
    }

    private fun parseDocxFile(file: File): LocalParseResult {
        return try {
            val fis = java.io.FileInputStream(file)
            val doc = org.apache.poi.xwpf.usermodel.XWPFDocument(fis)
            val text = StringBuilder()
            text.append("【Word文档】\n")

            // 提取段落文本
            val paragraphs = doc.paragraphs
            var charCount = 0
            val maxLength = 10000

            for (para in paragraphs) {
                val paraText = para.text
                if (paraText.isNotEmpty()) {
                    if (charCount + paraText.length > maxLength) {
                        text.append(paraText.take(maxLength - charCount))
                        break
                    }
                    text.append(paraText).append("\n")
                    charCount += paraText.length
                }
            }

            if (charCount >= maxLength) {
                text.append("\n...(内容过长，仅显示前${maxLength}字符)")
            }

            doc.close()
            fis.close()
            LocalParseResult.Success(text.toString())
        } catch (e: Exception) {
            LocalParseResult.Error("【Word文档解析失败】\n错误：${e.message}")
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

                    val cellValue = when (cell.cellType) {
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
        } catch (e: Exception) {
            LocalParseResult.Error("【Excel解析失败】\n错误：${e.message}")
        }
    }

    private fun parsePptxFile(file: File): LocalParseResult {
        return try {
            val fis = java.io.FileInputStream(file)
            val slideShow = org.apache.poi.xslf.usermodel.XMLSlideShow(fis)
            val text = StringBuilder()
            text.append("【PowerPoint演示文稿】\n幻灯片数量：${slideShow.slides.size}\n\n")

            var slideCount = 0
            val maxLength = 8000
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
        } catch (e: Exception) {
            LocalParseResult.Error("【PowerPoint解析失败】\n错误：${e.message}")
        }
    }

    private fun doSendWithAttachment(userText: String, fileName: String, base64Content: String, mimeType: String) {
        // 发送用户消息，包含文件信息
        val messageWithFile = "$userText\n\n[附件: $fileName]"
        viewModel.addMessage(AgentChatMessage(role = AgentChatMessage.Role.USER, text = messageWithFile))

        agentSession?.dispose()
        agentSession = null

        // 使用多模态API发送（仅图片/视频）
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
