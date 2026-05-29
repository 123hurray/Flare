package dev.dimension.flare.data.database.app.dao

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.OnConflictStrategy
import androidx.room3.Query
import dev.dimension.flare.data.database.app.model.DbAgentArtifact
import dev.dimension.flare.data.database.app.model.DbAgentConversation
import dev.dimension.flare.data.database.app.model.DbAgentEvent
import dev.dimension.flare.data.database.app.model.DbAgentMessage
import kotlinx.coroutines.flow.Flow

@Dao
internal interface AgentDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: DbAgentConversation)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: DbAgentMessage)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DbAgentEvent)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArtifact(artifact: DbAgentArtifact)

    @Query("SELECT * FROM DbAgentConversation ORDER BY updated_at DESC")
    fun conversations(): Flow<List<DbAgentConversation>>

    @Query("SELECT * FROM DbAgentConversation ORDER BY updated_at DESC")
    suspend fun conversationSnapshot(): List<DbAgentConversation>

    @Query("SELECT * FROM DbAgentConversation WHERE id = :id")
    suspend fun conversation(id: String): DbAgentConversation?

    @Query("SELECT * FROM DbAgentMessage WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun messages(conversationId: String): Flow<List<DbAgentMessage>>

    @Query("SELECT * FROM DbAgentEvent WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    fun events(conversationId: String): Flow<List<DbAgentEvent>>

    @Query("SELECT * FROM DbAgentMessage WHERE conversation_id = :conversationId ORDER BY created_at ASC")
    suspend fun messageSnapshot(conversationId: String): List<DbAgentMessage>

    @Query("SELECT * FROM DbAgentMessage ORDER BY created_at ASC")
    suspend fun allMessages(): List<DbAgentMessage>

    @Query("SELECT * FROM DbAgentEvent ORDER BY created_at ASC")
    suspend fun allEvents(): List<DbAgentEvent>

    @Query("SELECT * FROM DbAgentArtifact ORDER BY created_at ASC")
    suspend fun allArtifacts(): List<DbAgentArtifact>

    @Query("SELECT * FROM DbAgentArtifact WHERE message_id IN (:messageIds) ORDER BY created_at ASC")
    fun artifacts(messageIds: List<String>): Flow<List<DbAgentArtifact>>

    @Query("UPDATE DbAgentConversation SET title = :title, status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateConversation(
        id: String,
        title: String,
        status: String,
        updatedAt: Long,
    )

    @Query("UPDATE DbAgentConversation SET status = :status, updated_at = :updatedAt WHERE id = :id")
    suspend fun updateConversationStatus(
        id: String,
        status: String,
        updatedAt: Long,
    )

    @Query("DELETE FROM DbAgentConversation WHERE id = :id")
    suspend fun deleteConversation(id: String)
}
