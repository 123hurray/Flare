package dev.dimension.flare.ui.model

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
public data class UiKeywordFilter(
    val keyword: String,
    val isRegex: Boolean = false,
    val forTimeline: Boolean,
    val forNotification: Boolean,
    val forSearch: Boolean,
    val expiredAt: Instant?,
)
