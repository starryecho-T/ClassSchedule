package com.classschedule.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.classschedule.app.data.Terms

/** App 唯一数据库入口。 */
@Database(entities = [CourseEntity::class], version = 3, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {

    abstract fun courseDao(): CourseDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** v1 -> v2：courses 表新增 hidden_weeks_csv 列（M3 隐藏课程功能）。 */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE courses ADD COLUMN hidden_weeks_csv TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * v2 -> v3：courses 表新增 term_id 列（学期切换功能）。
         * 存量课程全部归入默认学期（2026-2027 第一学期，即 0.9.x 硬编码校历）。
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE courses ADD COLUMN term_id TEXT NOT NULL DEFAULT '${Terms.DEFAULT.id}'"
                )
            }
        }

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_schedule.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
            }
    }
}
