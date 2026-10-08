package expo.modules.facerecognition.database.models

data class Student(
    val studentId: String,
    val faceId: Long,
    val fullName: String,
    val year: Int?,
    val section: String?,
    val course: String
)