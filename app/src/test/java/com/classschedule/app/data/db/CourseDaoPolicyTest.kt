package com.classschedule.app.data.db

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 覆盖 DAO 默认方法的保护规则；实际 Room 事务回滚需 Android 集成测试验证。 */
class CourseDaoPolicyTest {
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
            dao.replaceAll(emptyList())
            fail("空列表替换应被拒绝")
        } catch (_: IllegalArgumentException) {
            // Expected: the guard runs before deleteAll.
        }

        assertEquals(listOf(existing), dao.rows)
        assertEquals(0, dao.deleteCalls)
        assertEquals(0, dao.insertCalls)
    }

    @Test
    fun validReplacementUsesOnlyIncomingCourses() = runBlocking {
        val incoming = listOf(course(2, "导入课程一"), course(3, "导入课程二"))
        val dao = InMemoryDao(listOf(course(1, "原课程")))

        dao.replaceAll(incoming)

        assertEquals(incoming, dao.rows)
        assertEquals(1, dao.deleteCalls)
        assertEquals(1, dao.insertCalls)
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
        override suspend fun getAllOnce(): List<CourseEntity> = rows.toList()
        override suspend fun getById(id: Long): CourseEntity? = rows.find { it.id == id }

        override suspend fun deleteAll() {
            deleteCalls++
            rows.clear()
        }

        override suspend fun insertAll(courses: List<CourseEntity>) {
            insertCalls++
            rows.addAll(courses)
        }

        override fun observeAll(): Flow<List<CourseEntity>> = error("Unused in these tests")
        override suspend fun upsert(course: CourseEntity): Long = error("Unused in these tests")
        override suspend fun delete(course: CourseEntity): Unit = error("Unused in these tests")
        override suspend fun deleteById(id: Long): Unit = error("Unused in these tests")
    }
}
