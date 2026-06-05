package dev.dimension.flare.data.network.xiaohongshu

import dev.dimension.flare.data.repository.LoginExpiredException
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.model.PlatformType
import dev.dimension.flare.model.xiaohongshuWebHost

internal object XhsErrorMapper {
    fun map(
        accountKey: MicroBlogKey?,
        code: Int?,
        message: String?,
    ): Throwable? =
        when (code) {
            461, 471 -> XhsVerificationRequiredException(accountKey, message ?: "Xiaohongshu verification required")
            300012 -> XhsIpBlockedException(message ?: "Xiaohongshu IP blocked")
            300015 -> XhsSignatureException(message ?: "Xiaohongshu signature rejected")
            -100 -> accountKey?.let { LoginExpiredException(it, PlatformType.Xiaohongshu) }
            else -> null
        }
}

public class XhsVerificationRequiredException(
    public val accountKey: MicroBlogKey?,
    message: String,
    public val url: String = "https://$xiaohongshuWebHost/explore",
) : IllegalStateException(message)

internal class XhsIpBlockedException(
    message: String,
) : IllegalStateException(message)

internal class XhsSignatureException(
    message: String,
) : IllegalStateException(message)

internal class XhsHttpException(
    val status: Int,
    val code: Int?,
    message: String,
) : IllegalStateException(message)
