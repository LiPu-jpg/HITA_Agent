package com.limpu.hitax.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns

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

    fun getDisplayName(context: Context, uri: Uri): String? {
        return runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        return@runCatching cursor.getString(index)
                    }
                }
                null
            }
        }.getOrNull() ?: uri.lastPathSegment
    }
}
