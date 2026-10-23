package com.classschedule.app.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.TermConfig
import com.classschedule.app.data.TermPreferences
import com.classschedule.app.data.backup.*
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.data.db.CourseEntity
import com.classschedule.app.data.db.toEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Operations live beyond view recreation; previews never auto-commit after process death. */
class BackupViewModel(application: Application) : AndroidViewModel(application) {
    data class Preview(
        val document: BackupDocument,
        val existing: List<CourseEntity>,
        val term: TermConfig,
        val confirmReplace: Boolean = false,
    ) {
        val additions: Int get() = BackupRestorePolicy.additions(existing, document.courses.map { it.toEntity(term.id) }).size
    }
    data class State(val busy: Boolean = false, val message: String = "", val preview: Preview? = null)
    private val mutable = MutableStateFlow(State())
    val state = mutable.asStateFlow()
    private val repo = CourseRepository(application, AppDatabase.getDatabase(application).courseDao())
    private val resolver = application.contentResolver

    fun export(uri: Uri) = runOperation {
        val term = TermPreferences.activeTerm(getApplication())
        repo.seedIfEmpty()
        val snapshot = BackupRestorePolicy.validatedCourses(repo.backupSnapshot(term), term)
        val version = getApplication<Application>().let { it.packageManager.getPackageInfo(it.packageName, 0).versionName }.orEmpty()
        val bytes = BackupCodec.encode(snapshot, version, term = term).toByteArray(Charsets.UTF_8)
        val stream = resolver.openOutputStream(uri, "wt") ?: error("无法打开目标文件")
        stream.use { it.write(bytes); it.flush() }
        State(message = "已备份 ${term.name} 的 ${snapshot.size} 条课程。请保留文件直到恢复验证完成。")
    }

    fun read(uri: Uri) = runOperation {
        val stream = resolver.openInputStream(uri) ?: error("无法读取所选文件")
        val term = TermPreferences.activeTerm(getApplication())
        val document = stream.use { BackupCodec.read(it, term) }
        repo.seedIfEmpty()
        if (document.courses.isEmpty()) State(message = "这是一份空课表备份，未修改现有课程。")
        else State(preview = Preview(document, repo.backupSnapshot(term), term))
    }

    fun confirmReplacement() {
        val current = mutable.value
        if (!current.busy) mutable.value = current.copy(preview = current.preview?.copy(confirmReplace = true))
    }
    fun cancelPreview() { if (!mutable.value.busy) mutable.value = State(message = "已取消恢复，课表未修改。") }
    fun message(text: String) { if (!mutable.value.busy) mutable.value = State(message = text) }

    fun restore(replace: Boolean) {
        val preview = mutable.value.preview ?: return
        if (replace && !preview.confirmReplace) return
        runOperation {
            try {
                // 恢复目标学期在预览时已锁定；替换前 DAO 会校验该学期快照未变化。
                val result = repo.restoreBackup(preview.document.courses, replace, preview.existing, preview.term)
                State(message = if (result.replaced) "已替换恢复 ${result.inserted} 条课程。"
                    else "恢复完成：新增 ${result.inserted} 条，跳过 ${result.skipped} 条重复安排；已有颜色与隐藏状态保留。")
            } catch (_: StaleBackupPreviewException) {
                State(message = "课表已变化，请核对最新数量并重新确认。",
                    preview = preview.copy(existing = repo.backupSnapshot(preview.term), confirmReplace = false))
            }
        }
    }
    private fun runOperation(block: suspend () -> State) {
        if (mutable.value.busy) return
        mutable.value = State(busy = true, message = "正在处理，请稍候…")
        viewModelScope.launch {
            mutable.value = try { withContext(Dispatchers.IO) { block() } }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) {
                val reason = when (error) {
                    is com.google.gson.stream.MalformedJsonException, is java.io.EOFException, is java.nio.charset.CharacterCodingException -> "文件不是完整有效的 UTF-8 JSON 备份，未修改课表"
                    is IllegalArgumentException -> error.message ?: "文件内容不符合备份格式"
                    is SecurityException -> "没有文件访问权限，请重新选择文件"
                    is java.io.IOException -> "文件读取或写入失败，请检查存储空间和文件提供方；残留导出文件可能不完整"
                    else -> "操作未完成，请重试；若为恢复操作，数据库事务会保留原课表"
                }
                State(message = reason)
            }
        }
    }
}
