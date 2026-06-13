package dev.dimension.flare.data.datasource.vvo

import dev.dimension.flare.data.network.vvo.VVOService
import dev.dimension.flare.data.network.vvo.model.User

internal suspend fun VVOService.userProfile(
    uid: String,
    st: String,
): User {
    val container = runCatching { getContainerIndex(type = "uid", value = uid) }.getOrNull()
    container?.data?.userInfo?.let { return it }

    val profile = runCatching { profileInfo(uid, st) }.getOrNull()
    profile?.data?.user?.let { return it }

    error(
        "VVO user profile not found for uid=$uid, " +
            "containerInfo(ok=${container?.ok}, errno=${container?.errno}, msg=${container?.msg}), " +
            "profileInfo(ok=${profile?.ok}, errno=${profile?.errno}, msg=${profile?.msg})",
    )
}
