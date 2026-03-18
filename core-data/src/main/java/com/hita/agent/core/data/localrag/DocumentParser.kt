package com.hita.agent.core.data.localrag

import java.io.InputStream

interface DocumentParser {
    fun canHandle(mimeType: String?, name: String?): Boolean
    fun parse(input: InputStream): String
}
