package dev.dimension.flare.data.datasource.jike

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

private val jikePagingJson = Json

internal fun String?.decodeJikeLoadMoreKey(): JsonObject? =
    this?.takeIf { it.isNotBlank() }?.let { value ->
        runCatching {
            jikePagingJson.decodeFromString<JsonObject>(value)
        }.getOrNull()
    }

internal fun JsonObject?.encodeJikeLoadMoreKey(): String? =
    this?.let {
        jikePagingJson.encodeToString(it)
    }
