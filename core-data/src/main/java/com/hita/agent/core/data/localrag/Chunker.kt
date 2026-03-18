package com.hita.agent.core.data.localrag

data class TextChunk(
    val content: String,
    val start: Int,
    val end: Int
)

class Chunker(private val chunkSize: Int = 1000) {
    fun chunk(text: String): List<TextChunk> {
        if (text.isBlank()) return emptyList()
        val chunks = mutableListOf<TextChunk>()
        var start = 0
        while (start < text.length) {
            val end = minOf(text.length, start + chunkSize)
            val slice = text.substring(start, end)
            chunks.add(TextChunk(slice, start, end))
            start = end
        }
        return chunks
    }
}
