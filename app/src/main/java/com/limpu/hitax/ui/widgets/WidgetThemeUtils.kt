package com.limpu.hitax.ui.widgets

import android.content.Context
import android.content.res.Configuration
import androidx.annotation.ColorInt
import com.limpu.hitax.R
import com.limpu.style.ThemeTools

object WidgetThemeUtils {
    data class Palette(
        val backgroundDrawableRes: Int,
        val dividerDrawableRes: Int,
        val locationChipDrawableRes: Int,
        val clockDrawableRes: Int,
        val placeholderDrawableRes: Int,
        @ColorInt val primaryTextColor: Int,
        @ColorInt val secondaryTextColor: Int,
        @ColorInt val accentColor: Int
    )

    fun useDarkPalette(mode: ThemeTools.MODE, systemNight: Boolean): Boolean {
        return when (mode) {
            ThemeTools.MODE.DARK -> true
            ThemeTools.MODE.LIGHT -> false
            ThemeTools.MODE.FOLLOW -> systemNight
        }
    }

    fun useDarkPalette(context: Context): Boolean {
        val systemNight =
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        return useDarkPalette(ThemeTools.getThemeMode(context), systemNight)
    }

    fun palette(context: Context): Palette {
        val dark = useDarkPalette(context)
        return Palette(
            backgroundDrawableRes = if (dark) {
                R.drawable.widget_today_background_dark
            } else {
                R.drawable.widget_today_background_light
            },
            dividerDrawableRes = if (dark) {
                R.drawable.widget_rounded_divider_dark
            } else {
                R.drawable.widget_rounded_divider_light
            },
            locationChipDrawableRes = if (dark) {
                R.drawable.widget_rounded_bar_dark
            } else {
                R.drawable.widget_rounded_bar
            },
            clockDrawableRes = if (dark) {
                R.drawable.widget_ic_clock_dark
            } else {
                R.drawable.widget_ic_clock
            },
            placeholderDrawableRes = if (dark) {
                R.drawable.widget_placeholder_timeline_dark
            } else {
                R.drawable.widget_placeholder_timeline
            },
            primaryTextColor = context.getColor(
                if (dark) R.color.widget_text_primary_dark else R.color.widget_text_primary_light
            ),
            secondaryTextColor = context.getColor(
                if (dark) R.color.widget_text_secondary_dark else R.color.widget_text_secondary_light
            ),
            accentColor = context.getColor(R.color.cruel_summer_primary)
        )
    }
}
