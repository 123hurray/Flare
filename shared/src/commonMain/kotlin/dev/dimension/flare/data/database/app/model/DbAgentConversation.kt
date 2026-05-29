package dev.dimension.flare.data.database.app.model

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
internal data class DbAgentConversation(
    @PrimaryKey
    val id: String,
    val title: String,
    val status: String,
    val created_at: Long,
    val updated_at: Long,
)

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DbAgentConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversation_id"),
    ],
)
internal data class DbAgentMessage(
    @PrimaryKey
    val id: String,
    val conversation_id: String,
    val role: String,
    val text: String,
    val created_at: Long,
)

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DbAgentConversation::class,
            parentColumns = ["id"],
            childColumns = ["conversation_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("conversation_id"),
    ],
)
internal data class DbAgentEvent(
    @PrimaryKey
    val id: String,
    val conversation_id: String,
    val message_id: String?,
    val type: String,
    val payload_json: String,
    val created_at: Long,
)

@Serializable
@Entity(
    foreignKeys = [
        ForeignKey(
            entity = DbAgentMessage::class,
            parentColumns = ["id"],
            childColumns = ["message_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("message_id"),
    ],
)
internal data class DbAgentArtifact(
    @PrimaryKey
    val id: String,
    val message_id: String,
    val type: String,
    val payload_json: String,
    val created_at: Long,
)
