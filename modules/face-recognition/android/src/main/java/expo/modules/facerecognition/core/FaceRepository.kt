package expo.modules.facerecognition.core

import android.content.Context
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.FaceFeature
import com.insightface.sdk.inspireface.base.FaceFeatureIdentity
import com.insightface.sdk.inspireface.base.SearchTopKResults
import java.io.File

class FaceRepository(
    context: Context
) {

    data class SearchResult(
        val matched: Boolean,
        val faceId: Long?,
        val confidence: Float,
        val threshold: Float
    )

    private val modelDirectory = File(
        File(
            context.applicationContext.filesDir,
            "face_hub"
        ),
        "Pikachu"
    )

    private val databaseFile = File(
        modelDirectory,
        "features.db"
    )

    private var hubAcquired = false

    fun open(): Boolean {
        synchronized(HUB_LOCK) {

            if (hubAcquired) {
                return true
            }

            val requestedPath =
                databaseFile.absolutePath

            if (hubReferences > 0) {

                if (
                    requestedPath != activeDatabasePath
                ) {
                    return false
                }

                hubReferences++
                hubAcquired = true

                return true
            }

            if (
                !modelDirectory.exists() &&
                !modelDirectory.mkdirs()
            ) {
                return false
            }

            val configuration =
                InspireFace.CreateFeatureHubConfiguration()
                    .setPrimaryKeyMode(
                        InspireFace.PK_MANUAL_INPUT
                    )
                    .setEnablePersistence(
                        true
                    )
                    .setPersistenceDbPath(
                        requestedPath
                    )
                    .setSearchThreshold(
                        InspireFace.GetRecommendedCosineThreshold()
                    )
                    .setSearchMode(
                        InspireFace.SEARCH_MODE_EXHAUSTIVE
                    )

            if (
                !InspireFace.FeatureHubDataEnable(
                    configuration
                )
            ) {
                return false
            }

            activeDatabasePath =
                requestedPath

            hubReferences = 1
            hubAcquired = true

            return true
        }
    }

    fun close() {
        synchronized(HUB_LOCK) {

            if (!hubAcquired) {
                return
            }

            hubAcquired = false

            hubReferences = maxOf(
                0,
                hubReferences - 1
            )

            if (hubReferences == 0) {
                InspireFace.FeatureHubDataDisable()
                activeDatabasePath = null
            }
        }
    }

    // used in recognition
    fun search(feature: FaceFeature?): SearchResult {
        val threshold = InspireFace.GetRecommendedCosineThreshold()

        if (!hubAcquired || feature == null) {
            return SearchResult(
                matched = false,
                faceId = null,
                confidence = Float.NaN,
                threshold = threshold
            )
        }

        val results: SearchTopKResults? = InspireFace.FeatureHubFaceSearchTopK(
            feature,
            1
        )

        if (
            results == null ||
            results.num <= 0 ||
            results.ids == null ||
            results.confidence == null ||
            results.ids.isEmpty() ||
            results.confidence.isEmpty()
        ) {
            return SearchResult(
                matched = false,
                faceId = null,
                confidence = Float.NaN,
                threshold = threshold
            )
        }

        val faceId = results.ids[0]
        val confidence = results.confidence[0]

        return SearchResult(
            matched = confidence >= threshold,
            faceId = if (
                confidence >= threshold
            ) {
                faceId
            } else {
                null
            },
            confidence = confidence,
            threshold = threshold
        )
    }

    // used in checking the existing db
    fun get(id: Long): FaceFeatureIdentity? {

        if (!hubAcquired) {
            return null
        }

        return InspireFace.FeatureHubGetFaceIdentity(
            id
        )
    }

    // used in inserting data
    fun insert(id: Long, feature: FaceFeature?): Boolean {

        if (!hubAcquired || feature == null) {
            return false
        }

        val identity =FaceFeatureIdentity.create(
            id,
            feature
        )

        return InspireFace.FeatureHubInsertFeature(
            identity
        )
    }

    fun clear() {
        synchronized(HUB_LOCK) {
            if (hubAcquired) {
                hubAcquired = false
                hubReferences = maxOf(0, hubReferences - 1)
            }
    
            if (hubReferences == 0) {
                InspireFace.FeatureHubDataDisable()
                activeDatabasePath = null
    
                if (databaseFile.exists()) {
                    databaseFile.delete()
                }
            }
        }
    }

    fun reset() {
        close()
    
        if (databaseFile.exists()) {
            databaseFile.delete()
        }
    
        open()
    }

    fun databaseFile(): File {
        return databaseFile
    }

    companion object {
        private val HUB_LOCK = Any()
        private var activeDatabasePath: String? = null
        private var hubReferences = 0
    }
}