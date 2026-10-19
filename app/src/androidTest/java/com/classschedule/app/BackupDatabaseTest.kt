package com.classschedule.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.classschedule.app.data.db.AppDatabase
import com.classschedule.app.data.db.CourseEntity
import com.classschedule.app.data.backup.StaleBackupPreviewException
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import com.classschedule.app.data.backup.BackupCodec
import com.classschedule.app.data.db.toDomain
import com.classschedule.app.data.db.toEntity
import android.content.Context
import android.net.Uri
import java.io.File

/** Uses an isolated in-memory Room DB; never touches the installed user's timetable. */
@RunWith(AndroidJUnit4::class)
class BackupDatabaseTest {
    private lateinit var db: AppDatabase
    private val row = CourseEntity(0, " 保留原文 ", "教师", "教室", 1, 1, 2, "1,2", 3, "2")
    @Before fun setup() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).build() }
    @After fun close() { db.close() }
    @Test fun failingInsertRollsBackDeletion() = runBlocking {
        val dao = db.courseDao()
        dao.insertAll(listOf(row))
        val before = dao.getAllOnce()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER reject_restore BEFORE INSERT ON courses WHEN NEW.name = 'FAIL' BEGIN SELECT RAISE(ABORT, 'forced test failure'); END")
        try {
            dao.restoreBackup(listOf(row, row.copy(name = "FAIL")), true, before)
            fail("Expected SQLite failure")
        } catch (_: android.database.sqlite.SQLiteException) {}
        assertEquals(before, dao.getAllOnce())
    }
    @Test fun replacementPreservesDuplicatesAndHiddenState() = runBlocking {
        val dao = db.courseDao()
        dao.insertAll(listOf(row.copy(name = "旧课程")))
        val result = dao.restoreBackup(listOf(row, row), true, dao.getAllOnce())
        assertEquals(2, result.inserted)
        assertEquals(listOf(row, row), dao.getAllOnce().map { it.copy(id = 0) })
    }
    @Test fun stalePreviewDoesNotDeleteConcurrentChanges() = runBlocking {
        val dao = db.courseDao()
        val preview = dao.getAllOnce()
        dao.insertAll(listOf(row))
        val before = dao.getAllOnce()
        try { dao.restoreBackup(listOf(row), true, preview); fail("Stale replacement accepted") }
        catch (_: StaleBackupPreviewException) {}
        assertEquals(before, dao.getAllOnce())
    }
    @Test fun appendIsIdempotentAndDoesNotOverwriteHiddenState() = runBlocking {
        val dao = db.courseDao()
        dao.insertAll(listOf(row))
        val before = dao.getAllOnce()
        repeat(2) { assertEquals(0, dao.restoreBackup(listOf(row.copy(hiddenWeeksCsv = "", colorIndex = 0)), false, emptyList()).inserted) }
        assertEquals(before, dao.getAllOnce())
    }
    @Test fun contentResolverFileRoundTripRestoresAllFields() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File.createTempFile("backup-test-", ".json", context.cacheDir)
        try {
            val uri = Uri.fromFile(file)
            val payload = BackupCodec.encode(listOf(row.toDomain(), row.toDomain()), "0.9.0")
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            val document = context.contentResolver.openInputStream(uri)!!.use(BackupCodec::read)
            val dao = db.courseDao()
            dao.restoreBackup(document.courses.map { it.toEntity() }, true, emptyList())
            assertEquals(listOf(row, row), dao.getAllOnce().map { it.copy(id = 0) })
        } finally { file.delete() }
    }
}
