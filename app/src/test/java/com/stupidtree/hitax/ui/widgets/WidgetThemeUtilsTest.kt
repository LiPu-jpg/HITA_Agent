package com.stupidtree.hitax.ui.widgets

import com.stupidtree.style.ThemeTools
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetThemeUtilsTest {
    @Test
    fun useDarkPalette_returnsTrueForDarkMode() {
        assertTrue(WidgetThemeUtils.useDarkPalette(ThemeTools.MODE.DARK, systemNight = false))
    }

    @Test
    fun useDarkPalette_returnsFalseForLightMode() {
        assertFalse(WidgetThemeUtils.useDarkPalette(ThemeTools.MODE.LIGHT, systemNight = true))
    }

    @Test
    fun useDarkPalette_followsSystemWhenConfiguredToFollow() {
        assertTrue(WidgetThemeUtils.useDarkPalette(ThemeTools.MODE.FOLLOW, systemNight = true))
        assertFalse(WidgetThemeUtils.useDarkPalette(ThemeTools.MODE.FOLLOW, systemNight = false))
    }
}
