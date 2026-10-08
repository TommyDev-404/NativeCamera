package expo.modules.facerecognition.database

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import expo.modules.facerecognition.database.models.Student

class StudentRepository(
    private val database: ScannerDatabase
) {

    fun insert(student: Student) {
        val values = ContentValues().apply {
            put("student_id", student.studentId)
            put("face_id", student.faceId)
            put("full_name", student.fullName)
            put("year", student.year)
            put("section", student.section)
            put("course", student.course)
        }

        database.writableDatabase.insertWithOnConflict(
            "students",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun insertAll(students: List<Student>) {
        val db = database.writableDatabase

        db.beginTransaction()

        try {
            for (student in students) {
                val values = ContentValues().apply {
                    put("student_id", student.studentId)
                    put("face_id", student.faceId)
                    put("full_name", student.fullName)
                    put("year", student.year)
                    put("section", student.section)
                    put("course", student.course)
                }

                db.insertWithOnConflict(
                    "students",
                    null,
                    values,
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getByFaceId(faceId: Long): Student? {
        database.readableDatabase.rawQuery(
            """
            SELECT
                student_id,
                face_id,
                full_name,
                year,
                section,
                course
            FROM students
            WHERE face_id = ?
            LIMIT 1
            """.trimIndent(),
            arrayOf(faceId.toString())
        ).use { cursor ->

            if (!cursor.moveToFirst()) {
                return null
            }

            val yearIndex =
                cursor.getColumnIndexOrThrow("year")

            val sectionIndex =
                cursor.getColumnIndexOrThrow("section")

            return Student(
                studentId = cursor.getString(
                    cursor.getColumnIndexOrThrow("student_id")
                ),

                faceId = cursor.getLong(
                    cursor.getColumnIndexOrThrow("face_id")
                ),

                fullName = cursor.getString(
                    cursor.getColumnIndexOrThrow("full_name")
                ),

                year = if (cursor.isNull(yearIndex)) {
                    null
                } else {
                    cursor.getInt(yearIndex)
                },

                section = if (cursor.isNull(sectionIndex)) {
                    null
                } else {
                    cursor.getString(sectionIndex)
                },

                course = cursor.getString(
                    cursor.getColumnIndexOrThrow("course")
                )
            )
        }
    }

    fun getAll(): List<Student> {
        val students = mutableListOf<Student>()

        database.readableDatabase.rawQuery(
            """
            SELECT
                student_id,
                face_id,
                full_name,
                year,
                section,
                course
            FROM students
            ORDER BY full_name
            """.trimIndent(),
            null
        ).use { cursor ->

            val studentIdIndex =
                cursor.getColumnIndexOrThrow("student_id")

            val faceIdIndex =
                cursor.getColumnIndexOrThrow("face_id")

            val fullNameIndex =
                cursor.getColumnIndexOrThrow("full_name")

            val yearIndex =
                cursor.getColumnIndexOrThrow("year")

            val sectionIndex =
                cursor.getColumnIndexOrThrow("section")

            val courseIndex =
                cursor.getColumnIndexOrThrow("course")

            while (cursor.moveToNext()) {
                students.add(
                    Student(
                        studentId = cursor.getString(
                            studentIdIndex
                        ),

                        faceId = cursor.getLong(
                            faceIdIndex
                        ),

                        fullName = cursor.getString(
                            fullNameIndex
                        ),

                        year = if (cursor.isNull(yearIndex)) {
                            null
                        } else {
                            cursor.getInt(yearIndex)
                        },

                        section = if (cursor.isNull(sectionIndex)) {
                            null
                        } else {
                            cursor.getString(sectionIndex)
                        },

                        course = cursor.getString(
                            courseIndex
                        )
                    )
                )
            }
        }

        return students
    }

    fun deleteAll() {
        database.writableDatabase.delete(
            "students",
            null,
            null
        )
    }
}