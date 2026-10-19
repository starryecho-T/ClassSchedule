package com.classschedule.app.data.importer

import java.net.URI

/** Accept a user-selected HTTPS entry without rewriting its path, query or fragment. */
object ImportUrlPolicy {
    const val DEFAULT_URL = "https://iedu.jlu.edu.cn"

    fun normalize(input: String): String {
        val value = input.trim()
        require(value.isNotEmpty()) { "请输入教务系统或学校 WebVPN 的网址" }
        require(value.length <= 8192 && value.none { it.isWhitespace() || it.isISOControl() || it == '\\' }) { "网址格式不正确，请检查空格和特殊字符" }
        val url = when {
            value.startsWith("//") -> "https:$value"
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://").containsMatchIn(value) -> value
            Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value) &&
                !Regex("^[^/?#:]+:[0-9]+(?:[/?#].*)?$").matches(value) -> value
            else -> "https://$value"
        }
        val parsed = try { URI(url) } catch (_: Exception) {
            throw IllegalArgumentException("网址格式不正确，请检查后重试")
        }
        require(parsed.scheme.equals("https", ignoreCase = true)) { "请输入 HTTPS 网址（https://），不支持此链接类型" }
        require(!parsed.host.isNullOrBlank() && parsed.rawUserInfo == null &&
            (parsed.port == -1 || parsed.port in 1..65535)) { "请输入有效的网站地址，网址中不能包含账号密码" }
        return "https" + url.substring(url.indexOf(':'))
    }
}
