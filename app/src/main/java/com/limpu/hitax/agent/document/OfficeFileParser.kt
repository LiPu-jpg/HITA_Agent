package com.limpu.hitax.agent.document

import android.content.Context
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.apache.poi.xslf.usermodel.XMLSlideShow
import java.io.File

/**
 * Excel 文件解析器
 */
class ExcelFileParser : FileParser {

    companion object {
        private const val TAG = "ExcelFileParser"
        private const val MAX_ROWS = 100
        private const val MAX_COLS = 20
    }

    override val supportedExtensions = listOf("xlsx")
    override val supportedMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    )

    override suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始解析Excel: $fileName")

                val tempFile = copyToTempFile(context, uri, fileName)

                XSSFWorkbook(tempFile).use { workbook ->
                    val text = StringBuilder()
                    text.append("【Excel表格】\n")
                    text.append("工作表数量：${workbook.numberOfSheets}\n\n")

                    // 只读取第一个工作表
                    val sheet = workbook.getSheetAt(0)
                    text.append("工作表1：${sheet.sheetName}\n")

                    var rowCount = 0
                    for (row in sheet) {
                        if (rowCount >= MAX_ROWS) {
                            text.append("\n...(行数过多，仅显示前${MAX_ROWS}行)")
                            break
                        }

                        val rowData = StringBuilder()
                        var colCount = 0

                        for (cell in row) {
                            if (colCount >= MAX_COLS) {
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

                    ParseResult.Success(text.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "Excel解析失败", e)
                ParseResult.Error("Excel解析失败: ${e.message}", cause = e)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "内存不足", e)
                ParseResult.Error("内存不足，请尝试更小的文件", cause = e, isRetryable = true)
            } finally {
                cleanupTempFile(context, fileName)
            }
        }
    }

    private fun copyToTempFile(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun cleanupTempFile(context: Context, fileName: String) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            if (tempFile.exists()) tempFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "清理临时文件失败", e)
        }
    }
}

/**
 * PowerPoint 文件解析器
 */
class PowerPointFileParser : FileParser {

    companion object {
        private const val TAG = "PowerPointFileParser"
        private const val MAX_SLIDES = 20
        private const val MAX_TEXT_LENGTH = 5000
    }

    override val supportedExtensions = listOf("pptx")
    override val supportedMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    )

    override suspend fun parse(context: Context, uri: Uri, fileName: String): ParseResult {
        return withContext(Dispatchers.IO) {
            try {
                Log.d(TAG, "开始解析PowerPoint: $fileName")

                val tempFile = copyToTempFile(context, uri, fileName)

                XMLSlideShow(tempFile.inputStream()).use { slideShow ->
                    val text = StringBuilder()
                    text.append("【PowerPoint演示文稿】\n")
                    text.append("幻灯片数量：${slideShow.slides.size}\n\n")

                    var slideCount = 0
                    var charCount = 0

                    for (slide in slideShow.slides) {
                        if (slideCount >= MAX_SLIDES) {
                            text.append("\n...(幻灯片过多，仅显示前${MAX_SLIDES}页)")
                            break
                        }

                        slideCount++
                        text.append("幻灯片${slideCount}：\n")

                        for (shape in slide.shapes) {
                            if (shape is org.apache.poi.xslf.usermodel.XSLFTextShape) {
                                val shapeText = shape.text.trim()
                                if (shapeText.isNotEmpty()) {
                                    if (charCount + shapeText.length > MAX_TEXT_LENGTH) {
                                        val remaining = MAX_TEXT_LENGTH - charCount
                                        if (remaining > 0) {
                                            text.append(shapeText.take(remaining))
                                        }
                                        charCount = MAX_TEXT_LENGTH
                                        break
                                    }
                                    text.append(shapeText).append("\n")
                                    charCount += shapeText.length
                                }
                            }
                        }

                        text.append("\n")

                        if (charCount >= MAX_TEXT_LENGTH) {
                            text.append("\n...(内容过长，仅显示前${MAX_TEXT_LENGTH}字)")
                            break
                        }
                    }

                    ParseResult.Success(text.toString())
                }
            } catch (e: Exception) {
                Log.e(TAG, "PowerPoint解析失败", e)
                ParseResult.Error("PowerPoint解析失败: ${e.message}", cause = e)
            } catch (e: OutOfMemoryError) {
                Log.e(TAG, "内存不足", e)
                ParseResult.Error("内存不足，请尝试更小的文件", cause = e, isRetryable = true)
            } finally {
                cleanupTempFile(context, fileName)
            }
        }
    }

    private fun copyToTempFile(context: Context, uri: Uri, fileName: String): File {
        val tempFile = File(context.cacheDir, fileName)
        context.contentResolver.openInputStream(uri)?.use { input ->
            tempFile.outputStream().use { output -> input.copyTo(output) }
        }
        return tempFile
    }

    private fun cleanupTempFile(context: Context, fileName: String) {
        try {
            val tempFile = File(context.cacheDir, fileName)
            if (tempFile.exists()) tempFile.delete()
        } catch (e: Exception) {
            Log.w(TAG, "清理临时文件失败", e)
        }
    }
}
