package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.instagram.INSTAGRAM_WEB_USER_AGENT
import dev.dimension.flare.data.network.instagram.InstagramImage
import dev.dimension.flare.data.network.instagram.InstagramMedia
import dev.dimension.flare.data.network.instagram.InstagramUser
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.UiDateTime
import dev.dimension.flare.ui.render.toUiPlainText
import dev.dimension.flare.ui.route.DeeplinkRoute
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.time.Instant

private val instagramMediaHeaders =
    persistentMapOf(
        "Referer" to "https://$instagramWebHost/",
        "User-Agent" to INSTAGRAM_WEB_USER_AGENT,
    )

internal fun InstagramUser.toUiProfile(accountKey: MicroBlogKey): UiProfile {
    val userKey = MicroBlogKey(id.ifBlank { username }, instagramWebHost)
    return UiProfile(
        key = userKey,
        handle = UiHandle(username.ifBlank { id }, "instagram"),
        avatar = profilePicUrl,
        nameInternal = fullName.ifBlank { username }.toUiPlainText(),
        platformType = PlatformType.Instagram,
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Profile.User(
                    accountType = AccountType.Specific(accountKey),
                    userKey = userKey,
                ),
            ),
        banner = null,
        description = biography.takeIf { it.isNotBlank() }?.toUiPlainText(),
        matrices =
            UiProfile.Matrices(
                fansCount = followerCount,
                followsCount = followingCount,
                statusesCount = mediaCount,
            ),
        mark =
            if (isVerified) {
                persistentListOf(UiProfile.Mark.Verified)
            } else {
                persistentListOf()
            },
        bottomContent = null,
    )
}

internal fun InstagramMedia.toUiTimeline(
    accountKey: MicroBlogKey,
    userOverride: UiProfile? = null,
): UiTimelineV2.Post {
    val statusKey = MicroBlogKey(id, instagramWebHost)
    return UiTimelineV2.Post(
        platformType = PlatformType.Instagram,
        images = images.map { it.toUiMedia() }.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = userOverride ?: user?.toUiProfile(accountKey),
        content = caption.toUiPlainText(),
        actions = persistentListOf<ActionMenu>(),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(takenAt.coerceAtLeast(0L) * 1000L)),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    accountType = AccountType.Specific(accountKey),
                    statusKey = statusKey,
                ),
            ),
        accountType = AccountType.Specific(accountKey),
    )
}

private fun InstagramImage.toUiMedia(): UiMedia.Image =
    UiMedia.Image(
        url = url,
        previewUrl = url,
        description = null,
        height = height,
        width = width,
        sensitive = false,
        customHeaders = instagramMediaHeaders,
    )
