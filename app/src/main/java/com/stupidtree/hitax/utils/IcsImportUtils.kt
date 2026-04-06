package com.stupidtree.hitax.utils

object IcsImportUtils {
    private val PICKER_MIME_TYPES = arrayOf(
        "text/calendar",
        "text/plain",
        "application/octet-stream",
        "application/ics",
        "application/ical",
        "application/x-ical",
        "text/x-vcalendar"
    )

    fun pickerMimeTypes(): Array<String> = PICKER_MIME_TYPES.copyOf()
}
