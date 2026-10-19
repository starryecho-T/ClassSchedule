package com.classschedule.app.ui

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import com.classschedule.app.widget.TodayCourseWidgetProvider

/** Persist the app's appearance independently of course backups. */
object ThemePreferences {
    private const val FILE = "appearance"
    private const val KEY = "night_mode"
    val modes = listOf(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_NO, AppCompatDelegate.MODE_NIGHT_YES)

    fun mode(context: Context): Int = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        .getInt(KEY, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        .takeIf { it in modes } ?: AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM

    fun applySaved(context: Context) {
        AppCompatDelegate.setDefaultNightMode(mode(context))
    }

    fun select(context: Context, mode: Int) {
        require(mode in modes)
        val app = context.applicationContext
        app.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY, mode).apply()
        AppCompatDelegate.setDefaultNightMode(mode)
        TodayCourseWidgetProvider.refreshAll(app)
    }

    /** RemoteViews must receive explicit colors when the app overrides the launcher theme. */
    fun widgetContext(context: Context): Context {
        val night = when (mode(context)) {
            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
            else -> context.applicationContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        }
        val config = Configuration(context.resources.configuration)
        config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or night
        return context.createConfigurationContext(config)
    }
}
