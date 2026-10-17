package com.classschedule.app.update

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** 远端最新版本信息。 */
data class UpdateInfo(
    /** 版本名，如「0.8.0」。 */
    val versionName: String,
    /** Release 页面地址，供浏览器打开。 */
    val releaseUrl: String,
    /** APK 直链；没有附件时为 null，此时只能引导到 Release 页。 */
    val apkUrl: String?,
    /** 更新说明（Release body 原文，可能为空）。 */
    val notes: String = "",
)

/**
 * 更新检查：只请求 GitHub 官方 `releases/latest`，不访问任何第三方。
 *
 * 匿名请求限流为 60 次/小时；任何失败一律静默返回 null，绝不打扰使用者。
 * 解析使用轻量正则而非 org.json，以便纯 JVM 单元测试覆盖。
 */
object UpdateChecker {

    private const val CONNECT_TIMEOUT_MS = 5_000
    private const val READ_TIMEOUT_MS = 8_000
    private const val MAX_BODY_CHARS = 200_000
    private val versionPattern = Regex("""v?(\d+)(?:\.(\d+))?(?:\.(\d+))?""")
    private val tagPattern = Regex(""""tag_name"\s*:\s*"([^"]*)"""")
    private val htmlUrlPattern = Regex(""""html_url"\s*:\s*"([^"]*)"""")
    private val bodyPattern = Regex(""""body"\s*:\s*"((?:[^"\\]|\\.)*)"""", RegexOption.DOT_MATCHES_ALL)
    private val downloadPattern = Regex(""""browser_download_url"\s*:\s*"([^"]*\.apk)"""", RegexOption.IGNORE_CASE)

    /**
     * 判断 [remote] 是否新于 [current]。
     * 无法解析（例如「nightly」）时视为无更新，避免在异常 tag 上反复弹窗。
     */
    fun isNewer(remote: String, current: String): Boolean {
        val r = parse(remote) ?: return false
        val c = parse(current) ?: return false
        repeat(3) { i -> if (r[i] != c[i]) return r[i] > c[i] }
        return false
    }

    /** 从 Release JSON 文本提取版本信息；tag 缺失时返回 null。 */
    fun parseRelease(json: String): UpdateInfo? {
        val tag = tagPattern.find(json)?.groupValues?.getOrNull(1)?.let(::normalize) ?: return null
        if (tag.isBlank()) return null
        val url = htmlUrlPattern.find(json)?.groupValues?.getOrNull(1)
            ?: "https://github.com/starryecho-T/ClassSchedule/releases"
        val apk = downloadPattern.find(json)?.groupValues?.getOrNull(1)?.unescapeJson()
        val notes = bodyPattern.find(json)?.groupValues?.getOrNull(1)?.unescapeJson() ?: ""
        return UpdateInfo(tag, url, apk, notes)
    }

    /**
     * 同步获取最新版本；无网络、无 Release 或解析失败时返回 null。
     * 必须在 IO 线程调用（如 `Dispatchers.IO` 协程内）。
     */
    fun fetchLatest(owner: String, repo: String): UpdateInfo? = runCatching {
        val url = URL("https://api.github.com/repos/$owner/$repo/releases/latest")
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.instanceFollowRedirects = true
            if (connection.responseCode !in 200..299) return null
            val text = connection.inputStream.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                    val buffer = StringBuilder()
                    var count = 0
                    while (count < MAX_BODY_CHARS) {
                        val line = reader.readLine() ?: break
                        buffer.append(line).append('\n')
                        count += line.length + 1
                    }
                    buffer.toString()
                }
            }
            parseRelease(text)
        } finally {
            connection.disconnect()
        }
    }.getOrNull()

    /** [raw] 形如 v0.8.0 -> 0.8.0。 */
    fun normalize(raw: String): String = raw.trim().removePrefix("v").removePrefix("V")

    private fun parse(raw: String): List<Int>? {
        val match = versionPattern.find(raw.trim()) ?: return null
        return listOf(
            match.groupValues[1].toIntOrNull() ?: 0,
            match.groupValues.getOrNull(2)?.toIntOrNull() ?: 0,
            match.groupValues.getOrNull(3)?.toIntOrNull() ?: 0,
        )
    }

    private fun String.unescapeJson(): String = buildString {
        val source = this@unescapeJson
        var i = 0
        while (i < source.length) {
            val ch = source[i++]
            if (ch == '\\' && i < source.length) {
                when (val next = source[i++]) {
                    'n' -> append('\n'); 't' -> append('\t')
                    'r' -> append('\r'); 'b' -> append('\b')
                    'f' -> append('')
                    'u' -> {
                        if (i + 4 <= source.length) {
                            append(source.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: next)
                            i += 4
                        } else append(next)
                    }
                    else -> append(next)
                }
            } else append(ch)
        }
    }
}
