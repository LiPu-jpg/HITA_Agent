package com.hita.agent.core.data.localrag

import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.zip.ZipInputStream

class PlainTextParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType?.startsWith("text/") == true || lowered.endsWith(".txt") || lowered.endsWith(".md")
    }

    override fun parse(input: InputStream): String {
        return BufferedReader(InputStreamReader(input, StandardCharsets.UTF_8)).use { reader ->
            reader.readText()
        }
    }
}

class PdfParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType == "application/pdf" || lowered.endsWith(".pdf")
    }

    override fun parse(input: InputStream): String {
        PDDocument.load(input).use { document ->
            val stripper = PDFTextStripper()
            return stripper.getText(document)
        }
    }
}

class DocxZipParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ||
            lowered.endsWith(".docx")
    }

    override fun parse(input: InputStream): String {
        return extractXmlFromZip(input, prefix = "word/")
    }
}

class PptxZipParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType == "application/vnd.openxmlformats-officedocument.presentationml.presentation" ||
            lowered.endsWith(".pptx")
    }

    override fun parse(input: InputStream): String {
        return extractXmlFromZip(input, prefix = "ppt/")
    }
}

class LegacyDocParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType == "application/msword" || lowered.endsWith(".doc")
    }

    override fun parse(input: InputStream): String {
        // Binary .doc is not parsed on device; keep metadata only (PARTIAL).
        return ""
    }
}

class LegacyPptParser : DocumentParser {
    override fun canHandle(mimeType: String?, name: String?): Boolean {
        val lowered = name?.lowercase().orEmpty()
        return mimeType == "application/vnd.ms-powerpoint" || lowered.endsWith(".ppt")
    }

    override fun parse(input: InputStream): String {
        // Binary .ppt is not parsed on device; keep metadata only (PARTIAL).
        return ""
    }
}

private fun extractXmlFromZip(input: InputStream, prefix: String): String {
    val builder = StringBuilder()
    ZipInputStream(input).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            if (!entry.isDirectory && name.startsWith(prefix) && name.endsWith(".xml")) {
                val xml = zip.bufferedReader(Charsets.UTF_8).readText()
                val text = stripXml(xml)
                if (text.isNotBlank()) {
                    builder.append(text).append('\n')
                }
            }
        }
    }
    return builder.toString()
}

private fun stripXml(xml: String): String {
    val noTags = xml.replace(Regex("<[^>]+>"), " ")
    return decodeEntities(noTags).replace(Regex("\\s+"), " ").trim()
}

private fun decodeEntities(text: String): String {
    return text
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
}
