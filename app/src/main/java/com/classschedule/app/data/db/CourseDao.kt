package com.classschedule.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** 课程表数据访问对象。 */
@Dao
interface CourseDao {

    @Query("SELECT * FROM courses ORDER BY day_of_week, start_period")
    fun observeAll(): Flow<List<CourseEntity>>

    @Query("SELECT * FROM courses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Query("SELECT COUNT(*) FROM courses")
    suspend fun count(): Int

    /** 有 id 更新、无 id 插入。 */
    @Upsert
    suspend fun upsert(course: CourseEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(courses: List<CourseEntity>)

    @Delete
    suspend fun delete(course: CourseEntity)

    @Query("DELETE FROM courses WHERE id = :id")
    suspend fun deleteById(id: Long)
}
