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
}
