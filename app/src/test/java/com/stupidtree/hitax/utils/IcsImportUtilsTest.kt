package com.stupidtree.hitax.utils

import org.junit.Assert.assertTrue
import org.junit.Test

class IcsImportUtilsTest {
    @Test
    fun pickerMimeTypes_supportCommonProviderLabelsForIcsFiles() {
        val mimeTypes = IcsImportUtils.pickerMimeTypes().toSet()

        assertTrue("text/calendar should be supported", mimeTypes.contains("text/calendar"))
        assertTrue("text/plain should be supported", mimeTypes.contains("text/plain"))
        assertTrue(
            "application/octet-stream should be supported",
            mimeTypes.contains("application/octet-stream")
        )
    }
}
