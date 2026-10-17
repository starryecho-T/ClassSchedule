package com.classschedule.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.classschedule.app.R
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.data.importer.JluScheduleParser
import com.classschedule.app.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONTokener
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** 用户在真实教务页面登录，抓取 DOM；未适配的页面可由用户主动导出快照。 */
class ImportActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var parseButton: MaterialButton
    private lateinit var importButton: MaterialButton
    private lateinit var snapshotButton: MaterialButton
    private var parsedCourses: List<Course> = emptyList()
    private var snapshot: File? = null
    private var exportSource: File? = null
    private var captureGeneration = 0
    private var captureJob: Job? = null
    private var applying = false
    private var exporting = false
    private var verifying = false
    private var capturedUrl: String? = null
    private var previewDialog: AlertDialog? = null
    private val captureScript by lazy {
        assets.open("import/capture_schedule.js").bufferedReader().use { it.readText() }
    }

    private val repo by lazy {
        CourseRepository(applicationContext, AppDatabase.getDatabase(applicationContext).courseDao())
    }

    private val exportSnapshot = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/html"),
    ) { uri ->
        val source = exportSource
        exportSource = null
        if (uri != null && source != null) {
            saveSnapshot(uri, source)
        } else {
            exporting = false
            snapshotButton.isEnabled = snapshot != null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        findViewById<MaterialToolbar>(R.id.import_toolbar)
            .setNavigationOnClickListener { if (!applying && !verifying) finish() }
        statusText = findViewById(R.id.import_status)
        parseButton = findViewById(R.id.btn_import_parse)
        importButton = findViewById(R.id.btn_import_apply)
        snapshotButton = findViewById(R.id.btn_import_snapshot)
        exportSource = savedInstanceState?.getString(STATE_EXPORT_SOURCE)?.let(::File)
        exporting = exportSource != null

        webView = findViewById(R.id.import_webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            userAgentString = DESKTOP_UA
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                invalidateCapture()
                statusText.setText(R.string.import_page_loading)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                parseButton.isEnabled = !applying && !verifying && captureJob?.isActive != true
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    invalidateCapture()
                    parseButton.isEnabled = !applying && !verifying
                    statusText.setText(R.string.import_page_failed)
                }
            }
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(JLU_PORTAL_URL)
        }
        onBackPressedDispatcher.addCallback(this) {
            if (!applying && !verifying) {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        }
        parseButton.setOnClickListener { captureCurrentPage() }
        importButton.setOnClickListener { confirmImport() }
        snapshotButton.setOnClickListener { confirmExport() }
    }

    /** 页面变化、重复抓取都使旧结果失效，防止导入上一页的课表。 */
    private fun invalidateCapture() {
        captureGeneration++
        captureJob?.cancel()
        captureJob = null
        parsedCourses = emptyList()
        capturedUrl = null
        previewDialog?.dismiss()
        previewDialog = null
        snapshot = null
        importButton.isEnabled = false
        snapshotButton.isEnabled = false
        parseButton.isEnabled = false
    }

    private fun captureCurrentPage() {
        if (applying || verifying) return
        invalidateCapture()
        val generation = captureGeneration
        statusText.setText(R.string.import_capturing)
        captureJob = lifecycleScope.launch {
            try {
                val capture = readCapture()
                val result = withContext(Dispatchers.Default) { JluScheduleParser.parse(capture) }
                if (generation != captureGeneration) return@launch
                if (!result.canImport) {
                    val reasons = result.issues.ifEmpty { listOf(getString(R.string.import_no_table)) }
                    statusText.text = getString(R.string.import_diagnostics, reasons.take(3).joinToString("\n") { it.take(120) })
                    try {
                        val file = withContext(Dispatchers.IO) {
                            JluScheduleParser.dump(applicationContext, capture.html)
                        }
                        if (generation == captureGeneration) {
                            snapshot = file
                            snapshotButton.isEnabled = !exporting
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: IOException) {
                        statusText.append("\n" + getString(R.string.import_snapshot_unavailable))
                    }
                } else {
                    parsedCourses = result.courses
                    capturedUrl = capture.sourceUrl
                    statusText.text = getString(R.string.import_recognized, parsedCourses.size)
                    importButton.isEnabled = true
                    confirmImport()
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                if (generation == captureGeneration) statusText.setText(R.string.import_timeout)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                if (generation == captureGeneration) statusText.setText(R.string.import_capture_failed)
            } finally {
                if (generation == captureGeneration) parseButton.isEnabled = !applying && !verifying
            }
        }
    }

    private suspend fun readCapture(): JluScheduleParser.Capture {
        val payload = withTimeout(15_000) {
            suspendCancellableCoroutine<String> { continuation ->
                webView.evaluateJavascript(captureScript) { value ->
                    if (!continuation.isActive) return@evaluateJavascript
                    try {
                        require(value.length <= 16 * 1024 * 1024) { "Page too large" }
                        val decoded = JSONTokener(value).nextValue() as? String
                        require(!decoded.isNullOrBlank()) { "Empty capture" }
                        continuation.resume(decoded)
                    } catch (error: Exception) {
                        continuation.resumeWithException(error)
                    }
                }
            }
        }
        return withContext(Dispatchers.Default) { JluScheduleParser.decodeCapture(payload) }
    }

    private fun confirmImport() {
        if (parsedCourses.isEmpty() || applying || verifying) return
        val courses = parsedCourses.toList()
        val generation = captureGeneration
        val sourceUrl = capturedUrl ?: return
        val preview = courses.joinToString("\n\n") { course ->
            val day = "一二三四五六日".getOrNull(course.dayOfWeek - 1) ?: '?'
            course.name + " · 周" + day + " " + course.startPeriod + "-" + course.endPeriod + "节\n" +
                Course.weeksText(course.weeks) + " · " + course.room + " · " + course.teacher
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
        }
        content.addView(TextView(this).apply {
            text = getString(R.string.import_review_notice) + "\n\n" + preview
            setTextIsSelectable(true)
        })
        val replace = CheckBox(this).apply { setText(R.string.import_replace_choice) }
        val verified = CheckBox(this).apply { setText(R.string.import_verify_choice) }
        content.addView(replace)
        content.addView(verified)
        val scroll = ScrollView(this).apply { addView(content) }
        val dialog = AlertDialog.Builder(this)
            .setTitle(R.string.import_preview_title)
            .setView(scroll)
            .setPositiveButton(R.string.import_confirm_add, null)
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        previewDialog = dialog
        dialog.setOnShowListener {
            val button = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            button.isEnabled = verified.isChecked
            verified.setOnCheckedChangeListener { _, checked -> button.isEnabled = checked }
            button.setOnClickListener {
                if (generation != captureGeneration || applying || verifying) return@setOnClickListener
                dialog.dismiss()
                if (replace.isChecked) {
                    val replacement = AlertDialog.Builder(this)
                        .setMessage(getString(R.string.import_replace_warning, courses.size))
                        .setPositiveButton(R.string.import_confirm_replace) { _, _ ->
                            verifyAndImport(courses, sourceUrl, generation, true)
                        }
                        .setNegativeButton(android.R.string.cancel, null).create()
                    previewDialog = replacement
                    replacement.show()
                } else {
                    verifyAndImport(courses, sourceUrl, generation, false)
                }
            }
        }
        dialog.show()
    }

    private fun verifyAndImport(courses: List<Course>, sourceUrl: String, generation: Int, replace: Boolean) {
        if (generation != captureGeneration || applying || verifying) return
        verifying = true
        parseButton.isEnabled = false
        importButton.isEnabled = false
        statusText.setText(R.string.import_verifying)
        lifecycleScope.launch {
            try {
                val current = readCapture()
                val result = withContext(Dispatchers.Default) { JluScheduleParser.parse(current) }
                if (generation != captureGeneration || sourceUrl != current.sourceUrl ||
                    !result.canImport || result.courses != courses) {
                    invalidateCapture()
                    statusText.setText(R.string.import_changed)
                    return@launch
                }
                applyCourses(courses, replace)
            } catch (cancelled: CancellationException) {
                if (cancelled is kotlinx.coroutines.TimeoutCancellationException) {
                    statusText.setText(R.string.import_timeout)
                } else throw cancelled
            } catch (_: Exception) {
                statusText.setText(R.string.import_capture_failed)
            } finally {
                verifying = false
                parseButton.isEnabled = !applying
                importButton.isEnabled = !applying && parsedCourses.isNotEmpty()
            }
        }
    }

    private suspend fun applyCourses(courses: List<Course>, replace: Boolean) {
        applying = true
        importButton.isEnabled = false
        parseButton.isEnabled = false
        statusText.setText(R.string.import_applying)
        try {
            val count = repo.importCourses(courses, replace)
            val message = when {
                replace -> getString(R.string.import_replaced, count)
                count == 0 -> getString(R.string.import_nothing_added)
                else -> getString(R.string.import_added, count)
            }
            Toast.makeText(this@ImportActivity, message, Toast.LENGTH_LONG).show()
            // 保留页面，分页课表可继续下一页追加；Flow 会同步刷新主课表。
            statusText.text = message
            parsedCourses = emptyList()
            capturedUrl = null
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            statusText.setText(R.string.import_apply_failed)
        } finally {
            applying = false
        }
    }

    private fun confirmExport() {
        val source = snapshot ?: return
        if (exporting) return
        AlertDialog.Builder(this)
            .setMessage(R.string.import_export_notice)
            .setPositiveButton(R.string.import_export_snapshot) { _, _ ->
                exportSource = source
                exporting = true
                snapshotButton.isEnabled = false
                exportSnapshot.launch("schedule_snapshot.html")
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun saveSnapshot(uri: Uri, source: File) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val output = contentResolver.openOutputStream(uri) ?: throw IOException("No output stream")
                    output.use { destination -> source.inputStream().use { it.copyTo(destination) } }
                }
                Toast.makeText(this@ImportActivity, R.string.import_export_done, Toast.LENGTH_LONG).show()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                Toast.makeText(this@ImportActivity, R.string.import_export_failed, Toast.LENGTH_LONG).show()
            } finally {
                exporting = false
                snapshotButton.isEnabled = snapshot != null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        outState.putString(STATE_EXPORT_SOURCE, exportSource?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        previewDialog?.dismiss()
        captureGeneration++
        captureJob?.cancel()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val JLU_PORTAL_URL = "https://iedu.jlu.edu.cn"
        private const val STATE_EXPORT_SOURCE = "export_snapshot_path"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
