package es.sergiotech.tickettracker

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import es.sergiotech.tickettracker.core.database.TicketDatabase
import es.sergiotech.tickettracker.domain.newId
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class DatabaseMigrationTest {
    @Test
    fun versionOneKeepsItsDocumentsAndPendingChanges() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-${newId()}.db"
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val schema =
            instrumentation.context.assets
                .open("${TicketDatabase::class.java.name}/1.json")
                .bufferedReader()
                .use { JSONObject(it.readText()).getJSONObject("database") }
        val file = context.getDatabasePath(name).apply { parentFile!!.mkdirs() }
        try {
            SQLiteDatabase.openOrCreateDatabase(file, null).use { old ->
                val entities = schema.getJSONArray("entities")
                for (index in 0 until entities.length()) {
                    val entity = entities.getJSONObject(index)
                    val table = entity.getString("tableName")
                    old.execSQL(entity.getString("createSql").replace("\u0024{TABLE_NAME}", table))
                    val indices = entity.getJSONArray("indices")
                    for (position in 0 until indices.length()) old.execSQL(
                        indices
                            .getJSONObject(position)
                            .getString("createSql")
                            .replace("\u0024{TABLE_NAME}", table)
                    )
                }
                val setup = schema.getJSONArray("setupQueries")
                for (index in 0 until setup.length()) old.execSQL(setup.getString(index))
                old.execSQL(
                    "INSERT INTO documents(id,ownerId,purchaseId,displayName,originalSha256,extractedText,state,createdAt) VALUES('doc','owner',NULL,'Original','hash','','LOCAL_ONLY',1)"
                )
                old.execSQL(
                    "INSERT INTO sync_outbox(entityId,ownerId,operationId,entityType,expectedRemoteRevision,payload,createdAt) VALUES('doc','owner','op','DOCUMENT',0,'{}',1)"
                )
                old.version = 1
            }
            // Actual Room opening runs the migration and validates all tables and indices.
            val migrated =
                Room.databaseBuilder(context, TicketDatabase::class.java, name)
                    .addMigrations(TicketDatabase.MIGRATION_1_2)
                    .build()
            try {
                val document = migrated.tickets().document("doc", "owner")!!
                assertEquals("Original", document.displayName)
                assertEquals(0L, document.remoteRevision)
                assertEquals(1, migrated.tickets().outbox("owner").size)
            } finally {
                migrated.close()
            }
        } finally {
            context.deleteDatabase(name)
        }
    }
}
