package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "ai_history")
data class AIHistory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val originalText: String,
    val modifiedText: String,
    val featureType: String, // e.g. "REWRITE", "GRAMMAR", "TOLERANCE", "REPLY"
    val extraInfo: String = "", // e.g. "Professional", "Casual"
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "saved_prompts")
data class SavedPrompt(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String,
    val promptContent: String,
    val category: String = "General",
    val isSystem: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "clipboard_history")
data class ClipboardHistory(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isStarred: Boolean = false
)

@Entity(tableName = "learned_words")
data class LearnedWord(
    @PrimaryKey val word: String,
    val frequency: Int = 1,
    val timestamp: Long = System.currentTimeMillis()
)
