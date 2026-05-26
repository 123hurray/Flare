package dev.dimension.flare.data.database.app.model

import androidx.room3.Entity
import androidx.room3.PrimaryKey
import kotlinx.serialization.Serializable

@Serializable
@Entity
internal data class DbKeywordFilter(
    @PrimaryKey
    val keyword: String,
    val is_regex: Long = 0L,
    val for_timeline: Long,
    val for_notification: Long,
    val for_search: Long,
    val expired_at: Long,
)
