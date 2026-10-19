package com.classschedule.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.material.bottomnavigation.BottomNavigationView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import androidx.appcompat.app.AppCompatDelegate
import android.graphics.Bitmap
import android.graphics.Canvas
import java.io.File
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import android.content.res.Configuration
import android.widget.TextView
import android.widget.FrameLayout
import com.classschedule.app.ui.ThemePreferences
import com.classschedule.app.widget.TodayCourseWidgetProvider

@RunWith(AndroidJUnit4::class)
class NavigationTest {
    @Test fun appearancePickerPersistsAndRestoresAcrossRecreation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = ThemePreferences.mode(context)
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                scenario.onActivity { it.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = R.id.tab_mine }
                for ((label, mode) in listOf("深色" to AppCompatDelegate.MODE_NIGHT_YES, "浅色" to AppCompatDelegate.MODE_NIGHT_NO, "跟随系统" to AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)) {
                    onView(withId(R.id.mine_theme)).perform(click())
                    onView(withText(label)).perform(click())
                    InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                    assertEquals(mode, ThemePreferences.mode(context))
                    scenario.recreate()
                    scenario.onActivity { activity ->
                        assertEquals(R.id.tab_mine, activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId)
                        assertEquals("外观模式：$label", activity.findViewById<TextView>(R.id.mine_theme).text.toString())
                        val expected = when (mode) {
                            AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
                            AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
                            else -> context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                        }
                        assertEquals(expected, activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)
                        val widget = TodayCourseWidgetProvider.buildViews(context, emptyList()).apply(context, FrameLayout(context))
                        assertEquals(activity.getColor(R.color.widget_text), widget.findViewById<TextView>(R.id.widget_body).currentTextColor)
                    }
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ThemePreferences.select(context, original) }
        }
    }
    @Test fun lightDarkTabsRenderAndKeepSelectedPage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = ThemePreferences.mode(context)
        try {
            for ((name, mode) in listOf("light" to AppCompatDelegate.MODE_NIGHT_NO, "dark" to AppCompatDelegate.MODE_NIGHT_YES)) {
                InstrumentationRegistry.getInstrumentation().runOnMainSync { ThemePreferences.select(context, mode) }
                ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                    for ((tab, id) in listOf("schedule" to R.id.tab_schedule, "overview" to R.id.tab_overview, "mine" to R.id.tab_mine)) {
                        scenario.onActivity { it.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId = id }
                        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
                        scenario.onActivity { activity ->
                            val view = activity.window.decorView
                            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                            view.draw(Canvas(bitmap))
                            File(activity.cacheDir, "qa-$name-$tab.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                            bitmap.recycle()
                            assertEquals(id, activity.findViewById<BottomNavigationView>(R.id.bottom_navigation).selectedItemId)
                        }
                    }
                }
            }
        } finally {
            InstrumentationRegistry.getInstrumentation().runOnMainSync { ThemePreferences.select(context, original) }
        }
    }
    @Test fun tabsAndRecreationKeepSingleInstances() {
        var weekBeforeRecreation = ""
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                nav.selectedItemId = R.id.tab_overview
                nav.selectedItemId = R.id.tab_mine
                nav.selectedItemId = R.id.tab_schedule
                activity.findViewById<android.view.View>(R.id.week_next).performClick()
                weekBeforeRecreation = activity.findViewById<android.widget.TextView>(R.id.week_label).text.toString()
                nav.selectedItemId = R.id.tab_mine
                assertEquals(3, activity.supportFragmentManager.fragments.size)
            }
            scenario.recreate()
            scenario.onActivity { activity ->
                val nav = activity.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                assertEquals(R.id.tab_mine, nav.selectedItemId)
                assertEquals(3, activity.supportFragmentManager.fragments.size)
                activity.onBackPressedDispatcher.onBackPressed()
                assertEquals(R.id.tab_schedule, nav.selectedItemId)
                assertEquals(weekBeforeRecreation, activity.findViewById<android.widget.TextView>(R.id.week_label).text.toString())
            }
        }
    }
}
