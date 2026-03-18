package com.hita.agent.ui.login

import org.junit.Assert.assertEquals
import org.junit.Test

class MainWebLoginActivityTest {
    @Test
    fun filterCookies_dropsBlankHosts() {
        val input = mapOf("a.com" to "", "b.com" to "k=v")
        val output = filterCookies(input)
        assertEquals(mapOf("b.com" to "k=v"), output)
    }
}
