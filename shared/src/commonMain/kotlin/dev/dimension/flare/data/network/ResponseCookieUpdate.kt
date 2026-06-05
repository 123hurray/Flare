package dev.dimension.flare.data.network

import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders

internal data class ResponseCookieUpdate(
    val updated: Map<String, String>,
    val removed: Set<String>,
) {
    val hasChanges: Boolean
        get() = updated.isNotEmpty() || removed.isNotEmpty()
}

internal fun HttpResponse.extractSetCookieUpdate(): ResponseCookieUpdate {
    val headers = headers.getAll(HttpHeaders.SetCookie).orEmpty()
    if (headers.isEmpty()) {
        return ResponseCookieUpdate(updated = emptyMap(), removed = emptySet())
    }
    val updated = mutableMapOf<String, String>()
    val removed = mutableSetOf<String>()
    headers.forEach { header ->
        val cookie = header.toCookieUpdate() ?: return@forEach
        if (cookie.isRemoval) {
            updated.remove(cookie.name)
            removed += cookie.name
        } else {
            removed.remove(cookie.name)
            updated[cookie.name] = cookie.value
        }
    }
    return ResponseCookieUpdate(updated = updated, removed = removed)
}

private data class CookieUpdate(
    val name: String,
    val value: String,
    val isRemoval: Boolean,
)

private fun String.toCookieUpdate(): CookieUpdate? {
    val parts = split(';')
    val pair = parts.firstOrNull()?.trim().orEmpty()
    val separatorIndex = pair.indexOf('=')
    if (separatorIndex <= 0) {
        return null
    }
    val name = pair.substring(0, separatorIndex).trim()
    if (name.isEmpty()) {
        return null
    }
    val value = pair.substring(separatorIndex + 1).trim().trim('"')
    val attributes = parts.drop(1).map { it.trim() }
    val isRemoval =
        value.isBlank() ||
            attributes.any { attr ->
                val lower = attr.lowercase()
                when {
                    lower.startsWith("max-age=") ->
                        lower.substringAfter("max-age=").toLongOrNull()?.let { it <= 0 } == true
                    lower.startsWith("expires=") ->
                        lower.contains("1970") || lower.contains("01 jan 1970")
                    else -> false
                }
            }
    return CookieUpdate(
        name = name,
        value = value,
        isRemoval = isRemoval,
    )
}
