package com.classschedule.app.data

import android.content.Context

/**
 * 当前学期偏好（沿用 [com.classschedule.app.ui.ThemePreferences] 的模式）。
 *
 * 只持久化学期 id，学期定义统一来自内置校历 [Terms]；
 * 没有记录或记录的 id 已不存在时回退到默认学期（升级用户行为不变）。
 * 无 ViewModel 的后台链路（提醒、小组件、备份）直接读本对象获取当前学期。
 */
object TermPreferences {
    private const val FILE = "term"
    private const val KEY = "term_id"

    fun activeTerm(context: Context): TermConfig =
        Terms.byId(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(KEY, null))

    fun select(context: Context, term: TermConfig) {
        context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            .edit().putString(KEY, term.id).apply()
    }
}
