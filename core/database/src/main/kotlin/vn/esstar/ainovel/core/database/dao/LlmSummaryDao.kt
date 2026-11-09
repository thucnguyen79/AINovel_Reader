package vn.esstar.ainovel.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import vn.esstar.ainovel.core.database.entity.LlmSummaryEntity

@Dao
interface LlmSummaryDao {
    @Query("SELECT * FROM llm_summaries WHERE chapter_id = :chapterId")
    suspend fun get(chapterId: Long): LlmSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: LlmSummaryEntity)

    @Query("DELETE FROM llm_summaries WHERE chapter_id = :chapterId")
    suspend fun delete(chapterId: Long)

    @Query("DELETE FROM llm_summaries")
    suspend fun deleteAll()
}
