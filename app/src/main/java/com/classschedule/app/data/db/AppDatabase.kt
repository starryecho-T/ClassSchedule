package com.classschedule.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/** App 唯一数据库入口。 */
@Database(entities = [CourseEntity::class], version = 2, exportSchema = true)
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

        fun getDatabase(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "class_schedule.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
