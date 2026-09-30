package com.classschedule.app.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/**
 * M4 教务导入（阶段一）：
 * WebView 承载 ieda.jlu.edu.cn，登录/验证码由用户在真实页面完成；
 * 进入「我的课表」后抓取渲染完成的 DOM 交给 [JluScheduleParser]。
 * 解析规则未就绪时会保存页面快照，供后续补充解析。
 */
class ImportActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var statusText: TextView
    private lateinit var importButton: MaterialButton
    private var parsedCourses: List<Course> = emptyList()

    private val repo by lazy {
        CourseRepository(applicationContext, AppDatabase.getDatabase(applicationContext).courseDao())
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_import)

        findViewById<MaterialToolbar>(R.id.import_toolbar)
            .setNavigationOnClickListener { finish() }
        statusText = findViewById(R.id.import_status)
        importButton = findViewById(R.id.btn_import_apply)

        webView = findViewById(R.id.import_webview)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = DESKTOP_UA
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }
        webView.webViewClient = WebViewClient()
        webView.loadUrl(JLU_PORTAL_URL)

        onBackPressedDispatcher.addCallback(this) {
            if (webView.canGoBack()) webView.goBack() else finish()
        }

        findViewById<MaterialButton>(R.id.btn_import_parse).setOnClickListener { captureCurrentPage() }
        importButton.setOnClickListener { confirmImport() }
    }

    /** 抓取 WebView 当前页面 HTML 并解析；evaluateJavascript 回调运行在主线程。 */
    private fun captureCurrentPage() {
        webView.evaluateJavascript(JS_CAPTURE) { value ->
            val html = runCatching { JSONTokener(value).nextValue() as? String }.getOrNull()
            if (html.isNullOrBlank() || html.length < MIN_HTML_LENGTH) {
                statusText.text = getString(R.string.import_parse_empty_page)
                return@evaluateJavascript
            }
            lifecycleScope.launch(Dispatchers.Default) {
                val courses = JluScheduleParser.parse(html)
                withContext(Dispatchers.Main) {
                    if (courses.isEmpty()) {
                        val file = JluScheduleParser.dump(applicationContext, html)
                        statusText.text = getString(R.string.import_parse_unsupported, file.absolutePath)
                        Toast.makeText(this@ImportActivity, R.string.import_snapshot_saved, Toast.LENGTH_SHORT).show()
                    } else {
                        parsedCourses = courses
                        statusText.text = getString(R.string.import_parse_ok, courses.size)
                        importButton.isEnabled = true
                    }
                }
            }
        }
    }

    private fun confirmImport() {
        if (parsedCourses.isEmpty()) return
        AlertDialog.Builder(this)
            .setMessage(getString(R.string.import_confirm, parsedCourses.size))
            .setPositiveButton(R.string.import_action_apply) { _, _ ->
                lifecycleScope.launch(Dispatchers.IO) {
                    repo.replaceAll(parsedCourses)
                    withContext(Dispatchers.Main) {
                        Toast.makeText(this@ImportActivity, R.string.import_done, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    companion object {
        private const val JLU_PORTAL_URL = "https://ieda.jlu.edu.cn"
        private const val DESKTOP_UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
        private const val JS_CAPTURE =
            "(function(){return document.documentElement.outerHTML;})()"
        private const val MIN_HTML_LENGTH = 500
    }
}
