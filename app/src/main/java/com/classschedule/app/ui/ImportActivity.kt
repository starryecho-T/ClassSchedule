package com.classschedule.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
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
import kotlinx.coroutines.withContext
import org.json.JSONTokener
import java.io.File
import java.io.IOException

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
            .setNavigationOnClickListener { if (!applying) finish() }
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
                parseButton.isEnabled = !applying
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    invalidateCapture()
                    parseButton.isEnabled = !applying
                    statusText.setText(R.string.import_page_failed)
                }
            }
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(JLU_PORTAL_URL)
        }
        onBackPressedDispatcher.addCallback(this) {
            if (!applying) {
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
        snapshot = null
        importButton.isEnabled = false
        snapshotButton.isEnabled = false
        parseButton.isEnabled = false
    }

    private fun captureCurrentPage() {
        if (applying) return
        invalidateCapture()
        val generation = captureGeneration
        statusText.setText(R.string.import_capturing)
        webView.evaluateJavascript(JS_CAPTURE) { value ->
            if (isFinishing || isDestroyed || generation != captureGeneration) return@evaluateJavascript
            val html = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
            if (html.isNullOrBlank()) {
                statusText.setText(R.string.import_parse_empty_page)
                parseButton.isEnabled = true
                return@evaluateJavascript
            }
            captureJob = lifecycleScope.launch {
                try {
                    val courses = withContext(Dispatchers.Default) { JluScheduleParser.parse(html) }
                    if (generation != captureGeneration) return@launch
                    if (courses.isEmpty()) {
                        val file = withContext(Dispatchers.IO) {
                            JluScheduleParser.dump(applicationContext, html)
                        }
                        if (generation != captureGeneration) return@launch
                        snapshot = file
                        snapshotButton.isEnabled = !exporting
                        statusText.setText(R.string.import_unsupported_with_export)
                    } else {
                        parsedCourses = courses
                        statusText.text = getString(R.string.import_parse_ok, courses.size)
                        importButton.isEnabled = true
                    }
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    if (generation == captureGeneration) statusText.setText(R.string.import_capture_failed)
                } finally {
                    if (generation == captureGeneration) parseButton.isEnabled = !applying
                }
            }
        }
    }

    private fun confirmImport() {
        if (parsedCourses.isEmpty() || applying) return
        val courses = parsedCourses.toList()
        val generation = captureGeneration
        val preview = courses.take(20).joinToString("\n\n") { course ->
            val day = "一二三四五六日".getOrNull(course.dayOfWeek - 1) ?: '?'
            course.name + " · 周" + day + " " + course.startPeriod + "-" + course.endPeriod + "节\n" +
                Course.weeksText(course.weeks) + " · " + course.room
        }
        val remaining = if (courses.size > 20) getString(R.string.import_preview_more, courses.size - 20) else ""
        AlertDialog.Builder(this)
            .setTitle(R.string.import_preview_title)
            .setMessage(preview + remaining + "\n\n" + getString(R.string.import_confirm, courses.size))
            .setPositiveButton(R.string.import_action_apply) { _, _ ->
                if (generation == captureGeneration && !applying) applyCourses(courses)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun applyCourses(courses: List<Course>) {
        applying = true
        importButton.isEnabled = false
        parseButton.isEnabled = false
        statusText.setText(R.string.import_applying)
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) { repo.replaceAll(courses) }
                Toast.makeText(this@ImportActivity, R.string.import_done, Toast.LENGTH_SHORT).show()
                finish()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                statusText.setText(R.string.import_apply_failed)
            } finally {
                applying = false
                parseButton.isEnabled = true
                importButton.isEnabled = parsedCourses.isNotEmpty()
            }
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
        captureGeneration++
        captureJob?.cancel()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val JLU_PORTAL_URL = "https://ieda.jlu.edu.cn"
        private const val STATE_EXPORT_SOURCE = "export_snapshot_path"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val JS_CAPTURE =
            "(function(){return document.documentElement ? document.documentElement.outerHTML : '';})()"
    }
}
