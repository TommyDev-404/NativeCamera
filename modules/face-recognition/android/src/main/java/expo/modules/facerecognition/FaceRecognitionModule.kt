package expo.modules.facerecognition

import com.insightface.sdk.inspireface.base.FaceFeature
import expo.modules.facerecognition.core.FaceRepository
import expo.modules.facerecognition.database.EventRepository
import expo.modules.facerecognition.database.ScannerDatabase
import expo.modules.facerecognition.database.StudentRepository
import expo.modules.facerecognition.database.SyncMetadataRepository
import expo.modules.facerecognition.database.models.Event
import expo.modules.facerecognition.database.models.Student
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONObject
import android.util.Log

class FaceRecognitionModule : Module() {
    
    private lateinit var scannerDatabase: ScannerDatabase
    private lateinit var studentRepository: StudentRepository
    private lateinit var eventRepository: EventRepository
    private lateinit var faceRepository: FaceRepository
    private lateinit var syncMetadataRepository: SyncMetadataRepository

    override fun definition() = ModuleDefinition {

        Name("FaceRecognition")

        OnCreate {
            val context = requireNotNull(appContext.reactContext)

            scannerDatabase = ScannerDatabase(context)
            studentRepository = StudentRepository(scannerDatabase)
            eventRepository = EventRepository(scannerDatabase)
            faceRepository = FaceRepository(context)
            syncMetadataRepository = SyncMetadataRepository(scannerDatabase)
        }

        Function("initializeDatabase") {
            try {
                val db = scannerDatabase.writableDatabase

                true

            } catch (error: Exception) {
                println(
                    "FaceRecognition: SQLite initialization error: " +
                        error.stackTraceToString()
                )

                false
            }
        }

        Function("getLastSyncAt") {
            syncMetadataRepository.getLastSyncedAt()
        }

        Function("syncDatabase") { data: String ->
            try {
                val json = JSONObject(data)
                val syncedAt = json.getString("synced_at")
                val studentsJson = json.getJSONArray("students")
                val faceEmbeddingsJson = json.getJSONArray("face_embeddings")
        
                Log.d(
                    "FaceRecognition",
                    "Sync received ${studentsJson.length()} students, " +
                        "${faceEmbeddingsJson.length()} face embeddings"
                )
        
                val students = mutableListOf<Student>()
        
                for (i in 0 until studentsJson.length()) {
                    val item = studentsJson.getJSONObject(i)
        
                    students.add(
                        Student(
                            studentId = item.getString("student_id"),
                            faceId = item.getLong("face_id"),
                            fullName = item.getString("full_name"),
                            year = if (item.isNull("year")) null else item.getInt("year"),
                            section = if (item.isNull("section")) null else item.getString("section"),
                            course = item.getString("course")
                        )
                    )
                }

                studentRepository.insertAll(students)
        
                Log.d(
                    "FaceRecognition",
                    "Students inserted successfully"
                )

                val faceHubOpened = faceRepository.open()
        
                if (!faceHubOpened) {
                    throw IllegalStateException(
                        "Failed to open InspireFace FeatureHub"
                    )
                }
        
                for (i in 0 until faceEmbeddingsJson.length()) {
                    val item = faceEmbeddingsJson.getJSONObject(i)
                    val faceId = item.getLong("id")
                    val embeddingJson = item.getJSONArray("embedding")
        
                    val embedding = FloatArray(embeddingJson.length()) { index ->
                        embeddingJson
                            .getDouble(index)
                            .toFloat()
                    }
        
                    val feature = FaceFeature()
        
                    feature.data = embedding
        
                    val existing = faceRepository.get(faceId)

                    if (existing != null) {
                        Log.d(
                            "FaceRecognition",
                            "Face $faceId already exists in FeatureHub, skipping..."
                        )
                    } else {
                        val inserted = faceRepository.insert(
                            id = faceId,
                            feature = feature
                        )

                        if (!inserted) {
                            throw IllegalStateException(
                                "Failed to insert face $faceId " +
                                    "into FeatureHub"
                            )
                        }

                        Log.d(
                            "FaceRecognition",
                            "FaceHub face inserted: faceId=$faceId"
                        )
                    }
                }
    
                syncMetadataRepository.updateLastSyncedAt(
                    syncedAt
                )
                
                Log.d(
                    "FaceRecognition",
                    "Database sync successful: lastSyncedAt=$syncedAt"
                )
                true
            } catch (error: Exception) {
                Log.e(
                    "FaceRecognition",
                    "Database sync failed",
                    error
                )
        
                false
            }
        }

        View(FaceRecognitionView::class) {
            Events("onStudentRecognized")
            
            OnViewDestroys { view ->
                view.cleanup()
            }
        }
    }

    companion object {
        private const val TAG = "FaceRecognition"
    }
}