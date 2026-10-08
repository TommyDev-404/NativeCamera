package expo.modules.facerecognition.database

import android.content.ContentValues
import expo.modules.facerecognition.database.models.Event

class EventRepository(
    private val database: ScannerDatabase
) {

    fun insert(event: Event) {
        val values = ContentValues().apply {
            put("id", event.id)
            put("name", event.name)
            put("start_date", event.startDate)
            put("end_date", event.endDate)
            put("start_time", event.startTime)
            put("end_time", event.endTime)
            put("morning_in", event.morningIn)
            put("morning_out", event.morningOut)
            put("afternoon_in", event.afternoonIn)
            put("afternoon_out", event.afternoonOut)
        }

        database.writableDatabase.insertWithOnConflict(
            "events",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertAll(events: List<Event>) {
        val db = database.writableDatabase

        db.beginTransaction()

        try {
            for (event in events) {
                val values = ContentValues().apply {
                    put("id", event.id)
                    put("name", event.name)
                    put("start_date", event.startDate)
                    put("end_date", event.endDate)
                    put("start_time", event.startTime)
                    put("end_time", event.endTime)
                    put("morning_in", event.morningIn)
                    put("morning_out", event.morningOut)
                    put("afternoon_in", event.afternoonIn)
                    put("afternoon_out", event.afternoonOut)
                }

                db.insertWithOnConflict(
                    "events",
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getById(id: Long): Event? {
        database.readableDatabase.rawQuery(
            """
            SELECT
                id,
                name,
                start_date,
                end_date,
                start_time,
                end_time,
                morning_in,
                morning_out,
                afternoon_in,
                afternoon_out
            FROM events
            WHERE id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(id.toString())
        ).use { cursor ->

            if (!cursor.moveToFirst()) {
                return null
            }

            return Event(
                id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                name = cursor.getString(cursor.getColumnIndexOrThrow("name")),
                startDate = cursor.getString(
                    cursor.getColumnIndexOrThrow("start_date")
                ),
                endDate = cursor.getString(
                    cursor.getColumnIndexOrThrow("end_date")
                ),
                startTime = cursor.getString(
                    cursor.getColumnIndexOrThrow("start_time")
                ),
                endTime = cursor.getString(
                    cursor.getColumnIndexOrThrow("end_time")
                ),
                morningIn = cursor.getStringOrNull("morning_in"),
                morningOut = cursor.getStringOrNull("morning_out"),
                afternoonIn = cursor.getStringOrNull("afternoon_in"),
                afternoonOut = cursor.getStringOrNull("afternoon_out")
            )
        }
    }

    fun getAll(): List<Event> {
        val events = mutableListOf<Event>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                id,
                name,
                start_date,
                end_date,
                start_time,
                end_time,
                morning_in,
                morning_out,
                afternoon_in,
                afternoon_out
            FROM events
            ORDER BY start_date DESC, start_time DESC
            """.trimIndent(),
            null
        ).use { cursor ->

            while (cursor.moveToNext()) {
                events.add(
                    Event(
                        id = cursor.getLong(
                            cursor.getColumnIndexOrThrow("id")
                        ),
                        name = cursor.getString(
                            cursor.getColumnIndexOrThrow("name")
                        ),
                        startDate = cursor.getString(
                            cursor.getColumnIndexOrThrow("start_date")
                        ),
                        endDate = cursor.getString(
                            cursor.getColumnIndexOrThrow("end_date")
                        ),
                        startTime = cursor.getString(
                            cursor.getColumnIndexOrThrow("start_time")
                        ),
                        endTime = cursor.getString(
                            cursor.getColumnIndexOrThrow("end_time")
                        ),
                        morningIn = cursor.getStringOrNull("morning_in"),
                        morningOut = cursor.getStringOrNull("morning_out"),
                        afternoonIn = cursor.getStringOrNull("afternoon_in"),
                        afternoonOut = cursor.getStringOrNull("afternoon_out")
                    )
                )
            }
        }

        return events
    }

    fun deleteAll() {
        database.writableDatabase.delete(
            "events",
            null,
            null
        )
    }

    private fun android.database.Cursor.getStringOrNull(
        columnName: String
    ): String? {
        val index = getColumnIndexOrThrow(columnName)

        return if (isNull(index)) {
            null
        } else {
            getString(index)
        }
    }
}