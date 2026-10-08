package expo.modules.facerecognition.database.models

data class Event(
    val id: Long,
    val name: String,
    val startDate: String,
    val endDate: String,
    val startTime: String,
    val endTime: String,
    val morningIn: String?,
    val morningOut: String?,
    val afternoonIn: String?,
    val afternoonOut: String?
)