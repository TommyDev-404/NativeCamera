package expo.modules.facerecognition.core

/** Local metadata paired with one FeatureHub identity. */
data class FaceRecord(
    val id: Long,
    val name: String,
    val cropPath: String,
    val updatedAt: Long
)