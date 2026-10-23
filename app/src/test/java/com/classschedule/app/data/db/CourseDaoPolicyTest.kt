package com.classschedule.app.data.db

import com.classschedule.app.data.Terms
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 覆盖 DAO 默认方法的保护规则；实际 Room 事务回滚需 Android 集成测试验证。 */
class CourseDaoPolicyTest {
    private val term = Terms.FALL_2026_2027

    @Test
    fun initializationPreservesExistingUserCourses() = runBlocking {
        val existing = course(1, "用户课程")
        val dao = InMemoryDao(listOf(existing))

        assertFalse(dao.insertIfEmpty(listOf(course(2, "内置课程"))))

        assertEquals(listOf(existing), dao.rows)
        assertEquals(0, dao.insertCalls)
        assertEquals(0, dao.deleteCalls)
    }

    @Test
    fun emptyDatabaseIsPopulatedOnlyOnce() = runBlocking {
        val firstSample = listOf(course(1, "内置课程 v1"))
        val dao = InMemoryDao()

        assertTrue(dao.insertIfEmpty(firstSample))
        assertFalse(dao.insertIfEmpty(listOf(course(2, "内置课程 v2"))))

        assertEquals(firstSample, dao.rows)
        assertEquals(1, dao.insertCalls)
        assertEquals(0, dao.deleteCalls)
    }

    @Test
    fun emptySeedDoesNotWriteAnything() = runBlocking {
        val dao = InMemoryDao()

        assertFalse(dao.insertIfEmpty(emptyList()))

        assertTrue(dao.rows.isEmpty())
        assertEquals(0, dao.insertCalls)
    }

    @Test
    fun emptyReplacementFailsBeforeDeletingAnyCourses() = runBlocking {
        val existing = course(1, "用户课程")
        val dao = InMemoryDao(listOf(existing))

        try {
            dao.replaceAll(term.id, emptyList())
            fail("空列表替换应被拒绝")
        } catch (_: IllegalArgumentException) {
            // Expected: the guard runs before deleteTerm.
        }

        assertEquals(listOf(existing), dao.rows)
        assertEquals(0, dao.deleteCalls)
        assertEquals(0, dao.insertCalls)
    }

    @Test
    fun validReplacementUsesOnlyIncomingCourses() = runBlocking {
        val incoming = listOf(course(2, "导入课程一"), course(3, "导入课程二"))
        val dao = InMemoryDao(listOf(course(1, "原课程")))

        dao.replaceAll(term.id, incoming)

        assertEquals(incoming, dao.rows)
        assertEquals(1, dao.deleteCalls)
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun replacementOnlyTouchesTheTargetTerm() = runBlocking {
        val otherTerm = course(1, "其他学期课程").copy(termId = Terms.SPRING_2026_2027.id)
        val dao = InMemoryDao(listOf(otherTerm, course(2, "本学期课程")))

        dao.replaceAll(term.id, listOf(course(3, "新课程")))

        // 其他学期的数据原样保留
        assertEquals(listOf(otherTerm, course(3, "新课程")), dao.rows)
        assertEquals(1, dao.deleteCalls)
    }

    @Test
    fun appendImportKeepsExistingCourseAndHiddenState() = runBlocking {
        val existing = course(1, "用户课程").copy(hiddenWeeksCsv = "1,3", colorIndex = 4)
        val dao = InMemoryDao(listOf(existing))
        val duplicate = existing.copy(id = 99, hiddenWeeksCsv = "", colorIndex = 0)
        val incoming = course(75, "导入课程").copy(hiddenWeeksCsv = "1")

        assertEquals(1, dao.importCourses(term, listOf(duplicate, incoming), false))

        assertEquals(existing, dao.rows.first())
        assertEquals(0L, dao.rows.last().id)
        assertEquals("", dao.rows.last().hiddenWeeksCsv)
        assertEquals(0, dao.deleteCalls)
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun repeatedImportReportsZeroAndDoesNotWriteAgain() = runBlocking {
        val incoming = listOf(course(0, "导入课程"))
        val dao = InMemoryDao()

        assertEquals(1, dao.importCourses(term, incoming, false))
        assertEquals(0, dao.importCourses(term, incoming, false))

        assertEquals(1, dao.rows.size)
        assertEquals(1, dao.insertCalls)
        assertEquals(0, dao.deleteCalls)
    }

    @Test
    fun replaceImportDeduplicatesAndClearsIncomingIdentity() = runBlocking {
        val incoming = course(91, "导入课程").copy(hiddenWeeksCsv = "1,2")
        val dao = InMemoryDao(listOf(course(1, "原课程")))

        assertEquals(1, dao.importCourses(term, listOf(incoming, incoming.copy(colorIndex = 2)), true))

        assertEquals(1, dao.rows.size)
        assertEquals("导入课程", dao.rows.single().name)
        assertEquals(0L, dao.rows.single().id)
        assertEquals("", dao.rows.single().hiddenWeeksCsv)
        assertEquals(1, dao.deleteCalls)
        assertEquals(1, dao.insertCalls)
    }

    @Test
    fun invalidImportBatchNeverDeletesOrInsertsInEitherMode() = runBlocking {
        for (replaceExisting in listOf(false, true)) {
            val existing = course(1, "原课程")
            val dao = InMemoryDao(listOf(existing))
            val incoming = listOf(course(0, "有效课程"),
                course(0, "无效课程").copy(weeksCsv = "18"))

            try {
                dao.importCourses(term, incoming, replaceExisting)
                fail("无效导入应该在写入前被拒绝")
            } catch (_: IllegalArgumentException) {
                // Expected: validate the complete batch before writes.
            }

            assertEquals(listOf(existing), dao.rows)
            assertEquals(0, dao.deleteCalls)
            assertEquals(0, dao.insertCalls)
        }
    }

    @Test
    fun emptyImportNeverDeletesOrInsertsInEitherMode() = runBlocking {
        for (replaceExisting in listOf(false, true)) {
            val existing = course(1, "原课程")
            val dao = InMemoryDao(listOf(existing))
            try {
                dao.importCourses(term, emptyList(), replaceExisting)
                fail("空导入应该被拒绝")
            } catch (_: IllegalArgumentException) {
                // Expected: an empty preview cannot clear the timetable.
            }
            assertEquals(listOf(existing), dao.rows)
            assertEquals(0, dao.deleteCalls)
            assertEquals(0, dao.insertCalls)
        }
    }

    private fun course(id: Long, name: String) = CourseEntity(
        id = id,
        name = name,
        teacher = "教师",
        room = "教室",
        dayOfWeek = 1,
        startPeriod = 1,
        endPeriod = 2,
        weeksCsv = "1,2,3",
        colorIndex = 0,
    )

    private class InMemoryDao(initial: List<CourseEntity> = emptyList()) : CourseDao {
        val rows = initial.toMutableList()
        var deleteCalls = 0
        var insertCalls = 0

        override suspend fun count(): Int = rows.size
        override suspend fun getAllOnce(termId: String): List<CourseEntity> =
            rows.filter { it.termId == termId }
        override suspend fun getById(id: Long): CourseEntity? = rows.find { it.id == id }
        override suspend fun deleteTerm(termId: String) {
            deleteCalls++
            rows.removeAll { it.termId == termId }
        }
        override suspend fun insertAll(courses: List<CourseEntity>) {
            insertCalls++
            rows.addAll(courses)
        }

        override fun observeAll(termId: String): Flow<List<CourseEntity>> = error("Unused in these tests")
        override suspend fun upsert(course: CourseEntity): Long = error("Unused in these tests")
        override suspend fun delete(course: CourseEntity): Unit = error("Unused in these tests")
        override suspend fun deleteById(id: Long): Unit = error("Unused in these tests")
    }
}
