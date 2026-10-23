package com.classschedule.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** UpdateChecker 纯逻辑测试（版本比较 + Release JSON 解析）。 */
class UpdateCheckerTest {

    @Test fun versionComparison() {
        assertTrue(UpdateChecker.isNewer("0.8.1", "0.8.0"))
        assertTrue(UpdateChecker.isNewer("v0.9", "0.8.0"))
        assertTrue(UpdateChecker.isNewer("1.0.0", "0.99.99"))
        assertFalse(UpdateChecker.isNewer("0.8.0.1", "0.8.0")) // 前三段相同即视为同版本
        assertFalse(UpdateChecker.isNewer("0.8.0", "0.8.0"))
        assertFalse(UpdateChecker.isNewer("0.7.9", "0.8.0"))
        assertFalse(UpdateChecker.isNewer("0.8", "0.8.1"))
    }

    @Test fun unparseableVersionMeansNoUpdate() {
        assertFalse(UpdateChecker.isNewer("nightly", "0.8.0"))
        assertFalse(UpdateChecker.isNewer("", "0.8.0"))
    }

    @Test fun normalizeStripsPrefix() {
        assertEquals("0.8.0", UpdateChecker.normalize("v0.8.0"))
        assertEquals("0.8.0", UpdateChecker.normalize("V0.8.0"))
        assertEquals("0.8.0", UpdateChecker.normalize(" 0.8.0 "))
    }

    @Test fun parseReleaseExtractsFields() {
        val json = """
            {"tag_name":"v0.8.1","html_url":"https://github.com/starryecho-T/ClassSchedule/releases/tag/v0.8.1",
             "assets":[{"browser_download_url":"https://github.com/starryecho-T/ClassSchedule/releases/download/v0.8.1/ClassSchedule-0.8.1.apk"}]}
        """.trimIndent()
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals("0.8.1", info.versionName)
        assertTrue(info.releaseUrl.endsWith("tag/v0.8.1"))
        assertTrue(info.apkUrl!!.endsWith(".apk"))
    }

    @Test fun parseReleaseWithoutAssets() {
        val json = """{"tag_name":"v0.9.0","html_url":"https://github.com/x/y/releases"}"""
        val info = UpdateChecker.parseRelease(json)!!
        assertEquals("0.9.0", info.versionName)
        assertNull(info.apkUrl)
    }

    @Test fun parseReleaseRejectsMissingTag() {
        assertNull(UpdateChecker.parseRelease("""{"html_url":"https://x"}"""))
        assertNull(UpdateChecker.parseRelease("""{"tag_name":""}"""))
    }

    @Test fun formatNotesCleansMarkdownForDialog() {
        val body = """
            # ClassSchedule v0.9.1

            发布日期：2026-10-19。版本号 0.9.1，versionCode 22。

            ## 教务导入改进

            - 新增网址输入与“打开”按钮，可输入 HTTPS 地址。
            - 新增“刷新”和“教务首页”入口。

            ## 安装与验证范围

            ClassSchedule-0.9.1.apk 沿用历史 debug 签名，支持覆盖同签名旧版本。

            按要求，本次发布仅构建 APK，不重新运行自动化测试或真机验证。

            APK SHA-256: `cfeb2e99c3db40bb`
        """.trimIndent()
        val notes = UpdateChecker.formatNotes(body)
        assertFalse("不应残留 Markdown 标题井号", notes.contains('#'))
        assertFalse("不应残留代码反引号", notes.contains('`'))
        assertFalse("不应包含 SHA-256 校验行", notes.contains("SHA-256"))
        assertFalse("首个一级标题与弹窗标题重复，应丢弃", notes.contains("ClassSchedule v0.9.1"))
        assertTrue(notes.startsWith("发布日期"))
        assertTrue(notes.contains("教务导入改进"))
        assertTrue(notes.contains("- 新增网址输入"))
        // 连续空行压缩为单个空行
        assertFalse(notes.contains("\n\n\n"))
    }

    @Test fun formatNotesTruncatesAtSentenceBoundary() {
        val paragraph = "这是一段足够长的说明文字，用来验证截断逻辑不会把句子拦腰切断。"
        val body = (1..40).joinToString("\n\n") { "第 $it 节：$paragraph" }
        val notes = UpdateChecker.formatNotes(body, maxChars = 200)
        assertTrue("超长说明应被截断", notes.length <= 201)
        assertTrue("截断处应补省略号", notes.endsWith("…"))
        assertFalse("不应在句号后残留半个句子", notes.endsWith("。…"))
    }

    @Test fun formatNotesKeepsShortNotesIntact() {
        val body = "# v0.9.0\n\n修复已知问题。"
        assertEquals("修复已知问题。", UpdateChecker.formatNotes(body))
        assertEquals("", UpdateChecker.formatNotes(""))
        assertEquals("", UpdateChecker.formatNotes("   \n  "))
    }
}
