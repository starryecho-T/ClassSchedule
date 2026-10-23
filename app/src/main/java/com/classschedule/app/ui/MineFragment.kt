package com.classschedule.app.ui

import android.content.Intent
import android.net.Uri
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.classschedule.app.MainActivity
import com.classschedule.app.R
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class MineFragment : CourseFragment(R.layout.fragment_mine) {
    private val backup: BackupViewModel get() = ViewModelProvider(requireActivity())[BackupViewModel::class.java]
    private var dialog: AlertDialog? = null
    private var themeDialog: AlertDialog? = null
    private val export = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) backup.export(uri) else backup.message("已取消备份。")
    }
    private val restore = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backup.read(uri) else backup.message("已取消选择文件。")
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val version = requireContext().packageManager.getPackageInfo(requireContext().packageName, 0).versionName
        view.findViewById<TextView>(R.id.mine_version).text = getString(R.string.backup_version, version)
        view.findViewById<View>(R.id.mine_import).setOnClickListener { startActivity(Intent(requireContext(), ImportActivity::class.java)) }
        val termButton = view.findViewById<TextView>(R.id.mine_term)
        termButton.text = getString(R.string.term_current_fmt, viewModel.term.value.name)
        termButton.setOnClickListener { showTermPicker() }
        view.findViewById<View>(R.id.mine_hidden).setOnClickListener { showHiddenCourses() }
        view.findViewById<View>(R.id.mine_update).setOnClickListener { (requireActivity() as MainActivity).checkForUpdate(true) }
        val themeNames = resources.getStringArray(R.array.appearance_modes)
        val themeButton = view.findViewById<TextView>(R.id.mine_theme)
        themeButton.text = getString(R.string.appearance_current, themeNames[ThemePreferences.modes.indexOf(ThemePreferences.mode(requireContext()))])
        themeButton.setOnClickListener {
            themeDialog?.dismiss()
            themeDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.appearance_title)
                .setSingleChoiceItems(themeNames, ThemePreferences.modes.indexOf(ThemePreferences.mode(requireContext()))) { chooser, index ->
                    chooser.dismiss()
                    themeButton.text = getString(R.string.appearance_current, themeNames[index])
                    ThemePreferences.select(requireContext(), ThemePreferences.modes[index])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
        view.findViewById<View>(R.id.mine_github).setOnClickListener {
            val url = getString(R.string.github_url)
            try { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            catch (_: android.content.ActivityNotFoundException) {
                (requireContext().getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("GitHub", url))
                Toast.makeText(requireContext(), R.string.github_open_failed, Toast.LENGTH_LONG).show()
            }
        }
        view.findViewById<View>(R.id.mine_export).setOnClickListener {
            val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
            try { export.launch("classschedule-backup-$timestamp.json") }
            catch (_: android.content.ActivityNotFoundException) { backup.message("未找到系统文件选择器。") }
        }
        view.findViewById<View>(R.id.mine_restore).setOnClickListener {
            try { restore.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
            catch (_: android.content.ActivityNotFoundException) { backup.message("未找到系统文件选择器。") }
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                launch { viewModel.courses.collect { /* Keep shared data current for hidden-course management. */ } }
                launch { viewModel.term.collect { term -> termButton.text = getString(R.string.term_current_fmt, term.name) } }
                launch { backup.state.collect { state ->
                    view.findViewById<View>(R.id.mine_export).isEnabled = !state.busy && state.preview == null
                    view.findViewById<View>(R.id.mine_restore).isEnabled = !state.busy && state.preview == null
                    view.findViewById<View>(R.id.backup_progress).visibility = if (state.busy) View.VISIBLE else View.GONE
                    view.findViewById<TextView>(R.id.backup_status).text = state.message
                    dialog?.dismiss()
                    dialog = state.preview?.let(::previewDialog)
                } }
            }
        }
    }
    private fun previewDialog(preview: BackupViewModel.Preview): AlertDialog {
        val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.backup_restore)
            .setNegativeButton(android.R.string.cancel) { _, _ -> backup.cancelPreview() }
            .setOnCancelListener { backup.cancelPreview() }
        if (preview.confirmReplace) {
            builder.setMessage("将移除当前 ${preview.existing.size} 条课程，并恢复备份中的 ${preview.document.courses.size} 条课程（包含重复记录与隐藏状态）。建议先备份当前课表。确定继续？")
                .setPositiveButton(R.string.backup_confirm_replace) { _, _ -> backup.restore(true) }
        } else {
            val added = preview.additions
            builder.setMessage("备份来自 ${preview.document.appVersion}，导出于 ${preview.document.exportedAt}。\n共 ${preview.document.courses.size} 条，预计新增 $added 条，跳过 ${preview.document.courses.size - added} 条。\n追加不会覆盖已有颜色和隐藏状态；文件内重复安排保留第一条。")
                .setPositiveButton(R.string.backup_append) { _, _ -> backup.restore(false) }
                .setNeutralButton(R.string.backup_replace) { _, _ -> backup.confirmReplacement() }
        }
        return builder.show()
    }
    override fun onPause() { dialog?.dismiss(); dialog = null; themeDialog?.dismiss(); themeDialog = null; super.onPause() }
    override fun onDestroyView() { dialog?.dismiss(); dialog = null; themeDialog?.dismiss(); themeDialog = null; super.onDestroyView() }
}
