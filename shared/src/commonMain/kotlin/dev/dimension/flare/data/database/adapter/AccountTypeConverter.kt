package dev.dimension.flare.data.database.adapter

import androidx.room3.TypeConverter
import dev.dimension.flare.common.decodeJson
import dev.dimension.flare.common.decodeProtobuf
import dev.dimension.flare.common.encodeJson
import dev.dimension.flare.common.encodeProtobuf
import dev.dimension.flare.model.DbAccountType
import dev.dimension.flare.model.AccountType
import dev.dimension.flare.model.MicroBlogKey
import dev.dimension.flare.ui.model.ClickEvent
import dev.dimension.flare.ui.model.UiProfile
import dev.dimension.flare.ui.model.UiIcon
import dev.dimension.flare.ui.model.UiRelation
import dev.dimension.flare.ui.model.UiTimelineV2
import dev.dimension.flare.ui.render.toUi
import kotlinx.serialization.SerializationException
import kotlin.time.Instant

internal class AccountTypeConverter {
    @TypeConverter
    fun fromString(value: String): DbAccountType = value.decodeJson()

    @TypeConverter
    fun fromEnum(value: DbAccountType): String = value.encodeJson()

    @TypeConverter
    fun fromUiProfile(value: UiProfile): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiProfile(value: ByteArray): UiProfile = value.decodeProtobuf()

    @TypeConverter
    fun fromUiTimelineV2(value: UiTimelineV2): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiTimelineV2(value: ByteArray): UiTimelineV2 =
        try {
            value.decodeProtobuf()
        } catch (e: SerializationException) {
            corruptTimelinePlaceholder(value)
        } catch (e: IllegalArgumentException) {
            corruptTimelinePlaceholder(value)
        }

    @TypeConverter
    fun fromUiRelation(value: UiRelation): ByteArray = value.encodeProtobuf()

    @TypeConverter
    fun toUiRelation(value: ByteArray): UiRelation = value.decodeProtobuf()
}

private fun corruptTimelinePlaceholder(value: ByteArray): UiTimelineV2 =
    UiTimelineV2.Message(
        statusKey = MicroBlogKey("corrupt_${value.contentHashCode()}", "local"),
        icon = UiIcon.Info,
        type = UiTimelineV2.Message.Type.Raw("缓存已失效，请刷新"),
        createdAt = Instant.fromEpochMilliseconds(0L).toUi(),
        clickEvent = ClickEvent.Noop,
        accountType = AccountType.Guest,
    )
