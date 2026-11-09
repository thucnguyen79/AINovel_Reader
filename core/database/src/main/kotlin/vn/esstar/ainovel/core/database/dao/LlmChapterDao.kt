package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import vn.esstar.ainovel.core.database.entity.LlmChapterEntity

@Dao
interface LlmChapterDao {
    @Query("SELECT * FROM llm_chapters WHERE chapter_id = :chapterId")
    suspend fun get(chapterId: Long): LlmChapterEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LlmChapterEntity)

    @Query("DELETE FROM llm_chapters WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: Long)

    @Query("SELECT COUNT(*) FROM llm_chapters")
    suspend fun count(): Int

    @Query("SELECT COALESCE(SUM(LENGTH(text) + LENGTH(title)), 0) FROM llm_chapters")
    suspend fun totalChars(): Long

    @Query("DELETE FROM llm_chapters")
    suspend fun deleteAll()
}
