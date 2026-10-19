package com.classschedule.app.data.importer

import org.junit.Assert.*
import org.junit.Test

class ImportUrlPolicyTest {
    @Test fun acceptsSchoolAndVpnPathsWithoutChangingQuery() {
        val url = "https://vpn.example.edu/https/iedu/course?service=https%3A%2F%2Fiedu.example.edu%2Fa%3Fx%3D1&next=a+b#week"
        assertEquals(url, ImportUrlPolicy.normalize(url))
        assertEquals("https://vpn.example.edu/login?service=https://iedu.example.edu/course",
            ImportUrlPolicy.normalize("vpn.example.edu/login?service=https://iedu.example.edu/course"))
    }
    @Test fun suppliesHttpsForBareHostAndPort() {
        assertEquals("https://iedu.jlu.edu.cn", ImportUrlPolicy.normalize("  iedu.jlu.edu.cn  "))
        assertEquals("https://school.example:8443/course?a=1", ImportUrlPolicy.normalize("school.example:8443/course?a=1"))
        assertEquals("https://school.example/course", ImportUrlPolicy.normalize("//school.example/course"))
        assertEquals("https://school.example/course", ImportUrlPolicy.normalize("HTTPS://school.example/course"))
    }
    @Test fun rejectsExecutableAndLocalSchemes() {
        listOf("javascript:alert(1)", "data:text/html,x", "file:///sdcard/test.html", "content://provider/item", "intent://login", "about:blank").forEach(::reject)
    }
    @Test fun rejectsCleartextAndEmbeddedCredentials() {
        listOf("http://school.example", "https://user:password@school.example", "https://school.example@evil.example").forEach(::reject)
    }
    @Test fun rejectsMalformedOrOversizedInput() {
        listOf("", " 	", "https://", "https:///course", "https://school.example:99999", "https://school.example:0", "https://school.example/path with spaces", "https://school.example/\npath", "https://school.example/\\path", "https://school.example/" + "a".repeat(8192)).forEach(::reject)
    }
    private fun reject(input: String) {
        try { ImportUrlPolicy.normalize(input); fail("Accepted an invalid URL") }
        catch (_: IllegalArgumentException) { }
    }
}
