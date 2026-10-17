package com.classschedule.app.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import com.classschedule.app.MainActivity
import com.classschedule.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 应用内更新检查入口（M5 之后的小功能）。
 *
 * 只访问 GitHub 官方 API 获取最新 Release，与当前安装版本比较；
 * 有更新时弹出说明对话框，用户选择后打开 Release 页面下载。
 * 全程异步且在后台线程执行，失败静默，不影响启动与使用。
 */
object UpdateCheckLauncher {

    private const val OWNER = "starryecho-T"
    private const val REPO = "ClassSchedule"
    private const val PREFS = "update_check"
    private const val KEY_SKIPPED = "skipped_version"

    /**
     * 检查更新。
     *
     * @param userInitiated true 时忽略“跳过此版本”记录（用户主动点击菜单）。
     */
    fun check(
        activity: MainActivity,
        currentVersionName: String,
        userInitiated: Boolean,
    ) {
        activity.lifecycleScope.launch(Dispatchers.IO) {
            val info = UpdateChecker.fetchLatest(OWNER, REPO) ?: run {
                if (userInitiated) {
                    activity.runOnUiThread {
                        MaterialAlertDialogBuilder(activity)
                            .setMessage(R.string.update_no_new)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                return@launch
            }
            val skipped = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_SKIPPED, null)
            val newer = UpdateChecker.isNewer(info.versionName, currentVersionName)
            if (!newer || (!userInitiated && skipped == info.versionName)) {
                if (userInitiated && !newer) {
                    activity.runOnUiThread {
                        MaterialAlertDialogBuilder(activity)
                            .setMessage(R.string.update_no_new)
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
                return@launch
            }
            activity.runOnUiThread { showUpdateDialog(activity, info) }
        }
    }

    private fun showUpdateDialog(context: MainActivity, info: UpdateInfo) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val message = buildString {
            append(context.getString(R.string.update_available_title, info.versionName))
            if (info.notes.isNotBlank()) append('\n').append(info.notes.take(600))
        }
        MaterialAlertDialogBuilder(context)
            .setTitle(R.string.update_check)
            .setMessage(message)
            .setPositiveButton(R.string.update_download) { _, _ ->
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(info.releaseUrl)))
            }
            .setNeutralButton(R.string.update_skip) { _, _ ->
                prefs.edit().putString(KEY_SKIPPED, info.versionName).apply()
            }
            .setNegativeButton(R.string.update_later, null)
            .show()
    }
}
