package dev.dimension.flare.data.datasource.instagram

import dev.dimension.flare.data.datasource.microblog.ActionMenu
import dev.dimension.flare.data.network.instagram.INSTAGRAM_WEB_USER_AGENT
import dev.dimension.flare.data.network.instagram.InstagramAttachment
import dev.dimension.flare.data.network.instagram.InstagramImage
import dev.dimension.flare.data.network.instagram.InstagramMedia
import dev.dimension.flare.data.network.instagram.InstagramUser
import dev.dimension.flare.data.network.instagram.InstagramVideo
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.instagramWebHost
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiHandle
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiMedia
import dev.dimension.flare.ui.model.UiNumber
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
    val accountType = AccountType.Specific(accountKey)
    return UiTimelineV2.Post(
        platformType = PlatformType.Instagram,
        images = attachments.map { it.toUiMedia() }.toImmutableList(),
        sensitive = false,
        contentWarning = null,
        user = userOverride ?: user?.toUiProfile(accountKey),
        content = caption.toUiPlainText(),
        actions =
            persistentListOf(
                ActionMenu.Item(
                    icon = UiIcon.Comment,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Comment),
                    count = UiNumber(commentCount),
                    clickEvent =
                        ClickEvent.Deeplink(
                            DeeplinkRoute.Status.Detail(
                                accountType = accountType,
                                statusKey = statusKey,
                            ),
                        ),
                ),
                ActionMenu.Item(
                    icon = UiIcon.Like,
                    text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Like),
                    count = UiNumber(likeCount),
                ),
                ActionMenu.Group(
                    displayItem =
                        ActionMenu.Item(
                            icon = UiIcon.More,
                            text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.More),
                        ),
                    actions =
                        persistentListOf(
                            ActionMenu.Item(
                                icon = UiIcon.Share,
                                text = ActionMenu.Item.Text.Localized(ActionMenu.Item.Text.Localized.Type.Share),
                                clickEvent =
                                    ClickEvent.Deeplink(
                                        DeeplinkRoute.Status.ShareSheet(
                                            statusKey = statusKey,
                                            accountType = accountType,
                                            shareUrl = shareUrl(),
                                        ),
                                    ),
                            ),
                        ),
                ),
            ),
        poll = null,
        statusKey = statusKey,
        card = null,
        createdAt = UiDateTime(Instant.fromEpochMilliseconds(takenAt.coerceAtLeast(0L) * 1000L)),
        clickEvent =
            ClickEvent.Deeplink(
                DeeplinkRoute.Status.Detail(
                    accountType = accountType,
                    statusKey = statusKey,
                ),
            ),
        accountType = accountType,
    )
}

private fun InstagramMedia.shareUrl(): String =
    if (code.isNotBlank()) {
        "https://$instagramWebHost/p/$code/"
    } else {
        "https://$instagramWebHost/p/$id/"
    }

private fun InstagramAttachment.toUiMedia(): UiMedia =
    when (this) {
        is InstagramImage -> toUiMedia()
        is InstagramVideo -> toUiMedia()
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

private fun InstagramVideo.toUiMedia(): UiMedia.Video =
    UiMedia.Video(
        url = url,
        thumbnailUrl = thumbnailUrl,
        description = null,
        height = height,
        width = width,
        customHeaders = instagramMediaHeaders,
    )
