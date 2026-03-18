package com.hita.agent.core.data.localrag

class ParserRegistry(private val parsers: List<DocumentParser>) {
    fun resolve(mimeType: String?, name: String?): DocumentParser? {
        return parsers.firstOrNull { it.canHandle(mimeType, name) }
    }

    companion object {
        fun empty(): ParserRegistry = ParserRegistry(emptyList())

        fun default(): ParserRegistry = ParserRegistry(
            listOf(
                PlainTextParser(),
                PdfParser(),
                DocxZipParser(),
                PptxZipParser(),
                LegacyDocParser(),
                LegacyPptParser()
            )
        )
    }
}
