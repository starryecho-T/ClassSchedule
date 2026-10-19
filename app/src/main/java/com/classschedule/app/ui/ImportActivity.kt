package com.classschedule.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.webkit.CookieManager
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.SslErrorHandler
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowCompat
import com.classschedule.app.R
import com.classschedule.app.data.CourseRepository
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.data.importer.JluScheduleParser
import com.classschedule.app.data.importer.ImportUrlPolicy
import com.classschedule.app.model.Course
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.delay
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
    private lateinit var address: TextInputEditText
    private lateinit var addressLayout: TextInputLayout
    private lateinit var openButton: MaterialButton
    private lateinit var toolbar: MaterialToolbar
    private lateinit var pageProgress: View
    private var pageLoading = true
    private var pageFailed = false
    private var loadTimeout: Job? = null
    private var requestedUrl = ImportUrlPolicy.DEFAULT_URL
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
            updateNavigationEnabled()
            snapshotButton.isEnabled = snapshot != null
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)
        toolbar = findViewById(R.id.import_toolbar)
        toolbar.setNavigationOnClickListener { if (!applying && !verifying) finish() }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.import_root)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        val light = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK != android.content.res.Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
        statusText = findViewById(R.id.import_status)
        parseButton = findViewById(R.id.btn_import_parse)
        importButton = findViewById(R.id.btn_import_apply)
        snapshotButton = findViewById(R.id.btn_import_snapshot)
        address = findViewById(R.id.import_address)
        addressLayout = findViewById(R.id.import_address_layout)
        openButton = findViewById(R.id.import_open)
        pageProgress = findViewById(R.id.import_page_progress)
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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request == null) return false
                if (applying || verifying) return true
                if (runCatching { ImportUrlPolicy.normalize(request.url.toString()) }.isSuccess) return false
                if (request.isForMainFrame) statusText.setText(R.string.import_link_blocked)
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                beginPageLoad()
                if (url != null && runCatching { ImportUrlPolicy.normalize(url) }.isSuccess) {
                    requestedUrl = url
                    updateAddress(url)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url != view?.url) return
                loadTimeout?.cancel()
                pageProgress.visibility = View.GONE
                if (pageLoading && !pageFailed) statusText.setText(R.string.import_page_ready)
                pageLoading = false
                parseButton.isEnabled = canCapture() && captureJob?.isActive != true
                if (url != null && !pageFailed) updateAddress(url)
                CookieManager.getInstance().flush()
            }

            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
            ) {
                if (request?.isForMainFrame == true) {
                    failPage(getString(R.string.import_network_failed, error?.errorCode ?: 0))
                }
            }

            override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) {
                if (request?.isForMainFrame == true) failPage(getString(R.string.import_http_failed, response?.statusCode ?: 0))
            }

            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                handler?.cancel()
                if (error?.url == view?.url || error?.url == requestedUrl) failPage(getString(R.string.import_ssl_failed))
            }
        }
        requestedUrl = savedInstanceState?.getString(STATE_URL)?.let { runCatching { ImportUrlPolicy.normalize(it) }.getOrNull() }
            ?: ImportUrlPolicy.DEFAULT_URL
        address.setText(requestedUrl)
        openButton.setOnClickListener { openAddress() }
        address.setOnEditorActionListener { _, action, _ ->
            if (action == EditorInfo.IME_ACTION_GO) { openAddress(); true } else false
        }
        toolbar.setOnMenuItemClickListener { item ->
            if (applying || verifying || exporting) return@setOnMenuItemClickListener true
            when (item.itemId) {
                R.id.import_reload -> {
                    beginPageLoad()
                    if (webView.url == null || webView.url == "about:blank") webView.loadUrl(requestedUrl) else webView.reload()
                    true
                }
                R.id.import_home -> { address.setText(ImportUrlPolicy.DEFAULT_URL); openAddress(); true }
                else -> false
            }
        }
        beginPageLoad()
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(requestedUrl)
        }
        onBackPressedDispatcher.addCallback(this) {
            if (!applying && !verifying) {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        }
        parseButton.setOnClickListener { captureCurrentPage() }
        importButton.setOnClickListener { confirmImport() }
        snapshotButton.setOnClickListener { confirmExport() }
        updateNavigationEnabled()
    }

    private fun updateAddress(url: String) {
        if (!address.hasFocus()) address.setText(url)
    }

    private fun openAddress() {
        if (applying || verifying || exporting) return
        val url = try { ImportUrlPolicy.normalize(address.text?.toString().orEmpty()) }
        catch (error: IllegalArgumentException) { addressLayout.error = error.message; return }
        addressLayout.error = null
        requestedUrl = url
        address.setText(url)
        address.clearFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(address.windowToken, 0)
        webView.stopLoading()
        beginPageLoad()
        webView.loadUrl(url)
    }

    private fun beginPageLoad() {
        invalidateCapture()
        pageLoading = true
        pageFailed = false
        pageProgress.visibility = View.VISIBLE
        statusText.setText(R.string.import_loading_page)
        loadTimeout?.cancel()
        loadTimeout = lifecycleScope.launch {
            delay(30_000)
            if (pageLoading && !pageFailed) statusText.setText(R.string.import_loading_slow)
        }
    }

    private fun failPage(message: String) {
        invalidateCapture()
        pageFailed = true
        pageLoading = false
        loadTimeout?.cancel()
        pageProgress.visibility = View.GONE
        statusText.text = message
    }

    private fun canCapture() = !pageLoading && !pageFailed && !applying && !verifying

    private fun updateNavigationEnabled() {
        val enabled = !applying && !verifying && !exporting
        openButton.isEnabled = enabled
        address.isEnabled = enabled
        toolbar.menu.findItem(R.id.import_reload).isEnabled = enabled
        toolbar.menu.findItem(R.id.import_home).isEnabled = enabled
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
        if (!canCapture()) return
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
                if (generation == captureGeneration) parseButton.isEnabled = canCapture()
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
        updateNavigationEnabled()
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
                updateNavigationEnabled()
                parseButton.isEnabled = canCapture()
                importButton.isEnabled = !applying && parsedCourses.isNotEmpty()
            }
        }
    }

    private suspend fun applyCourses(courses: List<Course>, replace: Boolean) {
        applying = true
        updateNavigationEnabled()
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
            updateNavigationEnabled()
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
                updateNavigationEnabled()
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
                updateNavigationEnabled()
                snapshotButton.isEnabled = snapshot != null
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        webView.saveState(outState)
        outState.putString(STATE_URL, requestedUrl)
        outState.putString(STATE_EXPORT_SOURCE, exportSource?.absolutePath)
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        previewDialog?.dismiss()
        captureGeneration++
        captureJob?.cancel()
        loadTimeout?.cancel()
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val STATE_URL = "import_current_url"
        private const val STATE_EXPORT_SOURCE = "export_snapshot_path"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
    }
}
