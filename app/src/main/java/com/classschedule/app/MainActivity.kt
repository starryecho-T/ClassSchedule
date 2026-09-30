package com.classschedule.app

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.classschedule.app.data.SampleData
import com.classschedule.app.ui.TimetableView
import com.google.android.material.appbar.MaterialToolbar

/**
 * 主页面：顶栏显示周次 / 学期信息，内容区为周视图课表。
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = getString(R.string.timetable_title, SampleData.CURRENT_WEEK)
            subtitle = getString(R.string.timetable_subtitle)
        }

        findViewById<TimetableView>(R.id.timetable).setup(SampleData.COURSES, SampleData.CURRENT_WEEK)
    }
}
