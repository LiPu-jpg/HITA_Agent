package com.hita.agent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AppContainerTest {
    @Test
    fun container_exposesLocalRagRepository() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val container = AppContainer(context)
        assertNotNull(container.localRagRepository)
    }
}
