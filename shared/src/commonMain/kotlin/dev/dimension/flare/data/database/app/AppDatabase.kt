package dev.dimension.flare.data.database.app

import androidx.room3.AutoMigration
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor
import androidx.room3.TypeConverters
import androidx.room3.migration.Migration
import androidx.sqlite.SQLiteConnection
import androidx.sqlite.execSQL
import dev.dimension.flare.data.database.app.dao.AccountDao
import dev.dimension.flare.data.database.app.dao.AgentDao
import dev.dimension.flare.data.database.app.dao.ApplicationDao
import dev.dimension.flare.data.database.app.dao.DraftDao
import dev.dimension.flare.data.database.app.dao.KeywordFilterDao
import dev.dimension.flare.data.database.app.dao.RssSourceDao
import dev.dimension.flare.data.database.app.dao.SearchHistoryDao

@Database(
    entities = [
        dev.dimension.flare.data.database.app.model.DbAccount::class,
        dev.dimension.flare.data.database.app.model.DbApplication::class,
        dev.dimension.flare.data.database.app.model.DbDraftGroup::class,
        dev.dimension.flare.data.database.app.model.DbDraftTarget::class,
        dev.dimension.flare.data.database.app.model.DbDraftMedia::class,
        dev.dimension.flare.data.database.app.model.DbKeywordFilter::class,
        dev.dimension.flare.data.database.app.model.DbSearchHistory::class,
        dev.dimension.flare.data.database.app.model.DbRssSources::class,
        dev.dimension.flare.data.database.app.model.DbAgentConversation::class,
        dev.dimension.flare.data.database.app.model.DbAgentMessage::class,
        dev.dimension.flare.data.database.app.model.DbAgentEvent::class,
        dev.dimension.flare.data.database.app.model.DbAgentArtifact::class,
    ],
    version = 11,
    autoMigrations = [
        AutoMigration(
            from = 3,
            to = 4,
        ),
        AutoMigration(
            from = 4,
            to = 5,
        ),
        AutoMigration(
            from = 5,
            to = 6,
        ),
        AutoMigration(
            from = 6,
            to = 7,
        ),
        AutoMigration(
            from = 7,
            to = 8,
        ),
    ],
    exportSchema = true,
)
@TypeConverters(
    dev.dimension.flare.data.database.adapter.MicroBlogKeyConverter::class,
    dev.dimension.flare.data.database.adapter.PlatformTypeConverter::class,
    dev.dimension.flare.data.database.app.model.DraftConverters::class,
    dev.dimension.flare.data.database.adapter.SubscriptionTypeConverter::class,
    dev.dimension.flare.data.database.adapter.RssDisplayModeConverter::class,
)
@ConstructedBy(AppDatabaseConstructor::class)
internal abstract class AppDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao

    abstract fun applicationDao(): ApplicationDao

    abstract fun draftDao(): DraftDao

    abstract fun keywordFilterDao(): KeywordFilterDao

    abstract fun searchHistoryDao(): SearchHistoryDao

    abstract fun rssSourceDao(): RssSourceDao

    abstract fun agentDao(): AgentDao

    companion object {
        val MIGRATION_8_9 =
            object : Migration(8, 9) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE DbRssSources ADD COLUMN displayMode TEXT NOT NULL DEFAULT 'FULL_CONTENT'",
                    )
                    connection.execSQL(
                        "UPDATE DbRssSources SET displayMode = 'OPEN_IN_BROWSER' WHERE openInBrowser = 1",
                    )
                    connection.execSQL(
                        "ALTER TABLE DbRssSources DROP COLUMN openInBrowser",
                    )
                }
            }
        val MIGRATION_9_10 =
            object : Migration(9, 10) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        "ALTER TABLE DbKeywordFilter ADD COLUMN is_regex INTEGER NOT NULL DEFAULT 0",
                    )
                }
            }
        val MIGRATION_10_11 =
            object : Migration(10, 11) {
                override suspend fun migrate(connection: SQLiteConnection) {
                    connection.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS DbAgentConversation (
                            id TEXT NOT NULL PRIMARY KEY,
                            title TEXT NOT NULL,
                            status TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            updated_at INTEGER NOT NULL
                        )
                        """.trimIndent(),
                    )
                    connection.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS DbAgentMessage (
                            id TEXT NOT NULL PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            role TEXT NOT NULL,
                            text TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(conversation_id) REFERENCES DbAgentConversation(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_DbAgentMessage_conversation_id ON DbAgentMessage(conversation_id)")
                    connection.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS DbAgentEvent (
                            id TEXT NOT NULL PRIMARY KEY,
                            conversation_id TEXT NOT NULL,
                            message_id TEXT,
                            type TEXT NOT NULL,
                            payload_json TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(conversation_id) REFERENCES DbAgentConversation(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_DbAgentEvent_conversation_id ON DbAgentEvent(conversation_id)")
                    connection.execSQL(
                        """
                        CREATE TABLE IF NOT EXISTS DbAgentArtifact (
                            id TEXT NOT NULL PRIMARY KEY,
                            message_id TEXT NOT NULL,
                            type TEXT NOT NULL,
                            payload_json TEXT NOT NULL,
                            created_at INTEGER NOT NULL,
                            FOREIGN KEY(message_id) REFERENCES DbAgentMessage(id) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                        """.trimIndent(),
                    )
                    connection.execSQL("CREATE INDEX IF NOT EXISTS index_DbAgentArtifact_message_id ON DbAgentArtifact(message_id)")
                }
            }
    }
}

// The Room compiler generates the `actual` implementations.
@Suppress("NO_ACTUAL_FOR_EXPECT")
internal expect object AppDatabaseConstructor : RoomDatabaseConstructor<AppDatabase> {
    override fun initialize(): AppDatabase
}
