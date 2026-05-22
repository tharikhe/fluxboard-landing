package com.example.data.local

import androidx.room.*
import com.example.data.model.AIHistory
import com.example.data.model.ClipboardHistory
import com.example.data.model.LearnedWord
import com.example.data.model.SavedPrompt
import kotlinx.coroutines.flow.Flow

@Dao
interface KeyboardDao {

    // AI History Queries
    @Query("SELECT * FROM ai_history ORDER BY timestamp DESC")
    fun getAIHistory(): Flow<List<AIHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAIHistory(history: AIHistory)

    @Query("DELETE FROM ai_history WHERE id = :id")
    suspend fun deleteAIHistoryById(id: String)

    @Query("DELETE FROM ai_history")
    suspend fun clearAIHistory()


    // Saved Prompts Queries
    @Query("SELECT * FROM saved_prompts ORDER BY timestamp DESC")
    fun getSavedPrompts(): Flow<List<SavedPrompt>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSavedPrompt(prompt: SavedPrompt)

    @Query("DELETE FROM saved_prompts WHERE id = :id")
    suspend fun deleteSavedPromptById(id: String)


    // Clipboard History Queries
    @Query("SELECT * FROM clipboard_history ORDER BY timestamp DESC")
    fun getClipboardHistory(): Flow<List<ClipboardHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertClipboardItem(item: ClipboardHistory)

    @Query("DELETE FROM clipboard_history WHERE id = :id")
    suspend fun deleteClipboardItemById(id: String)

    @Query("DELETE FROM clipboard_history WHERE isStarred = 0 AND timestamp < :timestamp")
    suspend fun pruneOldClipboardItems(timestamp: Long)

    @Query("DELETE FROM clipboard_history")
    suspend fun clearClipboardHistory()


    // Learned Words Queries
    @Query("SELECT * FROM learned_words ORDER BY frequency DESC, timestamp DESC")
    fun getLearnedWords(): Flow<List<LearnedWord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLearnedWord(word: LearnedWord)

    @Query("SELECT * FROM learned_words WHERE word = :word LIMIT 1")
    suspend fun getLearnedWord(word: String): LearnedWord?

    @Query("DELETE FROM learned_words WHERE word = :word")
    suspend fun deleteLearnedWord(word: String)
}
