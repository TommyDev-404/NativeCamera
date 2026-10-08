package expo.modules.facerecognition.database

class SyncMetadataRepository(
    private val database: ScannerDatabase
) {

    fun getLastSyncedAt(): String? {
        val db = database.readableDatabase

        db.query(
            "sync_metadata",
            arrayOf("last_synced_at"),
            "id = ?",
            arrayOf("1"),
            null,
            null,
            null
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val columnIndex = cursor.getColumnIndex("last_synced_at")

                if (columnIndex >= 0 && !cursor.isNull(columnIndex)) {
                    return cursor.getString(columnIndex)
                }
            }
        }

        return null
    }

    fun updateLastSyncedAt(timestamp: String) {
        val db = database.writableDatabase

        db.execSQL(
            """
               UPDATE sync_metadata
               SET last_synced_at = ?
               WHERE id = 1
            """.trimIndent(),
            arrayOf(timestamp)
        )
    }
}