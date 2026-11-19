package com.rusydiana.adzantime.data

import android.content.Context
import androidx.core.content.edit
import com.rusydiana.adzantime.R

/** How a placed widget is painted. Each instance on the home screen keeps its own choice. */
enum class WidgetTheme(val labelRes: Int) {
    DARK(R.string.widget_theme_dark),
    LIGHT(R.string.widget_theme_light),
    TRANSPARENT(R.string.widget_theme_transparent);

    /** Transparent sits on the wallpaper, so it keeps the light-on-dark palette. */
    val isLight: Boolean get() = this == LIGHT
}

/**
 * How large the widget draws its text, on top of the size the cell already earns. The widget
 * scales its type to the space it was given; this nudges that result for people who want it
 * denser or more readable than the automatic choice.
 */
enum class WidgetFontSize(val labelRes: Int, val multiplier: Float) {
    SMALL(R.string.widget_font_small, 0.85f),
    DEFAULT(R.string.widget_font_default, 1f),
    LARGE(R.string.widget_font_large, 1.25f),
    HUGE(R.string.widget_font_huge, 1.6f),
}

/**
 * Per-instance widget configuration, keyed by appWidgetId in the shared preferences file.
 * Read from the widget provider, which has no lifecycle of its own.
 */
object WidgetSettings {

    private const val FILE = "adzan_widgets"

    fun themeOf(context: Context, appWidgetId: Int): WidgetTheme =
        runCatching {
            WidgetTheme.valueOf(prefs(context).getString(key(appWidgetId, "theme"), null) ?: "")
        }.getOrDefault(WidgetTheme.DARK)

    fun setTheme(context: Context, appWidgetId: Int, theme: WidgetTheme) =
        prefs(context).edit { putString(key(appWidgetId, "theme"), theme.name) }

    fun fontSizeOf(context: Context, appWidgetId: Int): WidgetFontSize =
        runCatching {
            WidgetFontSize.valueOf(prefs(context).getString(key(appWidgetId, "font"), null) ?: "")
        }.getOrDefault(WidgetFontSize.DEFAULT)

    fun setFontSize(context: Context, appWidgetId: Int, size: WidgetFontSize) =
        prefs(context).edit { putString(key(appWidgetId, "font"), size.name) }

    fun showSyuruq(context: Context, appWidgetId: Int): Boolean =
        prefs(context).getBoolean(key(appWidgetId, "syuruq"), true)

    fun setShowSyuruq(context: Context, appWidgetId: Int, show: Boolean) =
        prefs(context).edit { putBoolean(key(appWidgetId, "syuruq"), show) }

    fun forget(context: Context, appWidgetId: Int) = prefs(context).edit {
        remove(key(appWidgetId, "theme"))
        remove(key(appWidgetId, "font"))
        remove(key(appWidgetId, "syuruq"))
    }

    private fun key(appWidgetId: Int, name: String) = "w${appWidgetId}_$name"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
}
