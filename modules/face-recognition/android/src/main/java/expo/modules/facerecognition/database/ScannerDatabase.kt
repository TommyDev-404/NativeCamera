package expo.modules.facerecognition.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ScannerDatabase(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE students (
                student_id TEXT PRIMARY KEY,
                face_id INTEGER UNIQUE NOT NULL,
                full_name TEXT NOT NULL,
                year INTEGER,
                section TEXT,
                course TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE sync_metadata (
                id INTEGER PRIMARY KEY CHECK (id = 1),
                last_synced_at TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            INSERT INTO sync_metadata (id, last_synced_at)
            VALUES (1, NULL)
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX index_students_face_id
            ON students(face_id)
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
    }

    companion object {
        private const val DATABASE_NAME = "estampmo.db"
        private const val DATABASE_VERSION = 2
    }
}