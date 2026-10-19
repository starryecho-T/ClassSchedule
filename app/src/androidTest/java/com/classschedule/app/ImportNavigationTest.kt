package com.classschedule.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.appcompat.app.AlertDialog
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.classschedule.app.ui.ImportActivity
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.textfield.TextInputLayout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

@RunWith(AndroidJUnit4::class)
class ImportNavigationTest {
    /** Exercise real navigation/capture callbacks using a local response, without school credentials. */
    @Test fun customAddressRecognizesRenderedScheduleAndShowsHttpErrors() {
        val requests = AtomicInteger()
        ActivityScenario.launch(ImportActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val web = activity.findViewById<WebView>(R.id.import_webview)
                web.stopLoading()
                val actual = web.webViewClient
                web.webViewClient = object : WebViewClient() {
                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) = actual.onPageStarted(view, url, favicon)
                    override fun onPageFinished(view: WebView?, url: String?) = actual.onPageFinished(view, url)
                    override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) = actual.onReceivedError(view, request, error)
                    override fun onReceivedHttpError(view: WebView?, request: WebResourceRequest?, response: WebResourceResponse?) = actual.onReceivedHttpError(view, request, response)
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = actual.shouldOverrideUrlLoading(view, request)
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse {
                        requests.incrementAndGet()
                        val failed = request?.url?.path == "/failed"
                        val html = when (request?.url?.path) {
                            "/entry" -> "<html><body><script>location.replace('https://portal.example.test/schedule?term=current');</script></body></html>"
                            "/failed" -> "<html><body>Service unavailable</body></html>"
                            else -> """<html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head><body><h2>测试课表</h2><table><tr><th>课程名称</th><th>授课教师</th><th>上课地点</th><th>星期</th><th>节次</th><th>周次</th></tr><tr><td>导航测试课程</td><td>示例教师</td><td>示例教室</td><td>星期一</td><td>1-2</td><td>1-16周</td></tr></table></body></html>"""
                        }
                        return WebResourceResponse("text/html", "UTF-8", if (failed) 503 else 200, if (failed) "Service Unavailable" else "OK", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(html.toByteArray()))
                    }
                }
                activity.findViewById<TextView>(R.id.import_address).text = "javascript:alert(1)"
                activity.findViewById<View>(R.id.import_open).performClick()
                assertNotNull(activity.findViewById<TextInputLayout>(R.id.import_address_layout).error)
                activity.findViewById<TextView>(R.id.import_address).text = "portal.example.test/entry"
                activity.findViewById<View>(R.id.import_open).performClick()
            }
            waitUntil(scenario) { it.findViewById<WebView>(R.id.import_webview).url?.contains("/schedule") == true && it.findViewById<View>(R.id.btn_import_parse).isEnabled }
            scenario.onActivity { activity ->
                assertEquals("https://portal.example.test/schedule?term=current", activity.findViewById<TextView>(R.id.import_address).text.toString())
                assertNull(activity.findViewById<TextInputLayout>(R.id.import_address_layout).error)
                val view = activity.window.decorView
                val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                File(activity.cacheDir, "qa-import-address.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
                activity.findViewById<View>(R.id.btn_import_parse).performClick()
            }
            waitUntil(scenario) { it.findViewById<View>(R.id.btn_import_apply).isEnabled }
            var preview: AlertDialog? = null
            scenario.onActivity { activity ->
                val field = ImportActivity::class.java.getDeclaredField("previewDialog").apply { isAccessible = true }
                val dialog = field.get(activity) as AlertDialog
                preview = dialog
                assertTrue(dialog.isShowing)
                fun containsCourse(view: View): Boolean =
                    (view is TextView && view.text.contains("导航测试课程")) ||
                        (view is ViewGroup && (0 until view.childCount).any { containsCourse(view.getChildAt(it)) })
                assertTrue(containsCourse(dialog.window!!.decorView))
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
            }
            // AlertDialog queues dismissal after the button callback on the main thread.
            waitUntil(scenario) { preview?.isShowing == false }
            val beforeRefresh = requests.get()
            scenario.onActivity { it.findViewById<MaterialToolbar>(R.id.import_toolbar).menu.performIdentifierAction(R.id.import_reload, 0) }
            waitUntil(scenario) { requests.get() > beforeRefresh && it.findViewById<View>(R.id.btn_import_parse).isEnabled }
            scenario.onActivity { activity ->
                assertFalse(activity.findViewById<View>(R.id.btn_import_apply).isEnabled)
                activity.findViewById<TextView>(R.id.import_address).text = "https://portal.example.test/failed"
                activity.findViewById<View>(R.id.import_open).performClick()
            }
            waitUntil(scenario) { it.findViewById<TextView>(R.id.import_status).text.contains("503") }
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity {
                assertFalse(it.findViewById<View>(R.id.btn_import_parse).isEnabled)
                assertFalse(it.findViewById<View>(R.id.btn_import_apply).isEnabled)
                assertEquals(View.GONE, it.findViewById<View>(R.id.import_page_progress).visibility)
            }
        }
    }

    private fun waitUntil(scenario: ActivityScenario<ImportActivity>, condition: (ImportActivity) -> Boolean) {
        val until = SystemClock.uptimeMillis() + 10_000
        while (SystemClock.uptimeMillis() < until) {
            var ready = false
            scenario.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        fail("Import page did not reach the expected state")
    }
}
