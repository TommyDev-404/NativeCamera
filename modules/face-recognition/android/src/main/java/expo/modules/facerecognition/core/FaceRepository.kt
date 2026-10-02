package expo.modules.facerecognition.core

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import com.insightface.sdk.inspireface.InspireFace
import com.insightface.sdk.inspireface.base.FaceFeature
import com.insightface.sdk.inspireface.base.FaceFeatureIdentity
import com.insightface.sdk.inspireface.base.FeatureHubConfiguration
import com.insightface.sdk.inspireface.base.SearchTopKResults
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale

/**
 * Model-scoped FeatureHub storage. Each model gets its own native DB, crop directory,
 * metadata preferences and ID sequence; no feature or image is shared across models.
 */
class FaceRepository(
    context: Context,
    model: FaceModelPrefs.Model
) {

    data class SearchResult(
        val matched: Boolean,
        val record: FaceRecord?,
        val confidence: Float,
        val threshold: Float
    )

    data class InsertResult(
        val success: Boolean,
        val record: FaceRecord?
    )

    private val metadata: SharedPreferences
    private val modelDirectory: File
    private val cropDirectory: File
    private val databaseFile: File

    private var hubAcquired = false

    init {
        val app = context.applicationContext

        modelDirectory = File(
            File(app.filesDir, "face_hub"),
            model.sdkName
        )

        cropDirectory = File(modelDirectory, "crops")
        databaseFile = File(modelDirectory, "features.db")

        metadata = app.getSharedPreferences(
            "face_records_${model.sdkName}",
            Context.MODE_PRIVATE
        )
    }

    /**
     * Must be called after GlobalLaunch and from the repository's SDK executor.
     */
    fun open(): Boolean {
        synchronized(HUB_LOCK) {
            if (hubAcquired) {
                return true
            }

            val requestedPath = databaseFile.absolutePath

            if (hubReferences > 0) {
                if (requestedPath != activeDatabasePath) {
                    return false
                }

                hubReferences++
                hubAcquired = true
                return true
            }

            if (!cropDirectory.exists() && !cropDirectory.mkdirs()) {
                return false
            }

            val configuration = InspireFace.CreateFeatureHubConfiguration()
                .setPrimaryKeyMode(InspireFace.PK_MANUAL_INPUT)
                .setEnablePersistence(true)
                .setPersistenceDbPath(requestedPath)
                .setSearchThreshold(InspireFace.GetRecommendedCosineThreshold())
                .setSearchMode(InspireFace.SEARCH_MODE_EXHAUSTIVE)

            if (!InspireFace.FeatureHubDataEnable(configuration)) {
                return false
            }

            activeDatabasePath = requestedPath
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
            hubReferences = maxOf(0, hubReferences - 1)

            if (hubReferences == 0) {
                InspireFace.FeatureHubDataDisable()
                activeDatabasePath = null
            }
        }
    }

    fun query(keyword: String?): List<FaceRecord> {
        val normalized = keyword
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: ""

        val records = mutableListOf<FaceRecord>()

        for ((key, value) in metadata.all) {
            if (!key.startsWith(KEY_PREFIX) || value !is String) {
                continue
            }

            val record = decode(value)

            if (
                record != null &&
                (
                    normalized.isEmpty() ||
                    record.name.lowercase(Locale.ROOT).contains(normalized) ||
                    record.id.toString().contains(normalized)
                )
            ) {
                records.add(record)
            }
        }

        records.sortWith { left, right ->
            right.updatedAt.compareTo(left.updatedAt)
        }

        return records
    }

    /**
     * Searches the active model's native FeatureHub and joins the best ID to metadata.
     */
    fun search(feature: FaceFeature?): SearchResult {
        val threshold = InspireFace.GetRecommendedCosineThreshold()

        if (!hubAcquired || feature == null) {
            return SearchResult(
                false,
                null,
                Float.NaN,
                threshold
            )
        }

        val results: SearchTopKResults? =
            InspireFace.FeatureHubFaceSearchTopK(feature, 1)

        if (
            results == null ||
            results.num <= 0 ||
            results.ids == null ||
            results.confidence == null ||
            results.ids.isEmpty() ||
            results.confidence.isEmpty()
        ) {
            return SearchResult(
                false,
                null,
                Float.NaN,
                threshold
            )
        }

        val confidence = results.confidence[0]
        val record = get(results.ids[0])

        return SearchResult(
            record != null && confidence >= threshold,
            record,
            confidence,
            threshold
        )
    }

    fun get(id: Long): FaceRecord? {
        return decode(
            metadata.getString(key(id), null)
        )
    }

    fun insert(
        name: String?,
        feature: FaceFeature?,
        crop: Bitmap?
    ): InsertResult {
        if (!hubAcquired || feature == null || crop == null) {
            return InsertResult(false, null)
        }

        var id = maxOf(
            1L,
            metadata.getLong(KEY_NEXT_ID, 1L)
        )

        while (metadata.contains(key(id))) {
            id++
        }

        val cropFile = cropFile(id)
        val stagedCrop = stageCrop(cropFile, crop)

        if (stagedCrop == null) {
            return InsertResult(false, null)
        }

        val cropBackup = backupFile(cropFile)
        val hadPreviousCrop = cropFile.exists()

        val identity = FaceFeatureIdentity.create(id, feature)

        if (!InspireFace.FeatureHubInsertFeature(identity)) {
            stagedCrop.delete()
            return InsertResult(false, null)
        }

        if (!commitStagedCrop(cropFile, stagedCrop, cropBackup)) {
            InspireFace.FeatureHubFaceRemove(id)
            return InsertResult(false, null)
        }

        val record = FaceRecord(
            id = id,
            name = normalizedName(name, id),
            cropPath = cropFile.absolutePath,
            updatedAt = System.currentTimeMillis()
        )

        val saved = metadata.edit()
            .putString(key(id), encode(record))
            .putLong(KEY_NEXT_ID, id + 1)
            .commit()

        if (!saved) {
            InspireFace.FeatureHubFaceRemove(id)
            restoreCrop(
                cropFile,
                cropBackup,
                hadPreviousCrop
            )
            return InsertResult(false, null)
        }

        cropBackup.delete()

        return InsertResult(true, record)
    }

    /**
     * Passing null feature/crop performs a metadata-only rename.
     */
    fun update(
        id: Long,
        name: String?,
        feature: FaceFeature?,
        crop: Bitmap?
    ): Boolean {
        val old = get(id)

        if (!hubAcquired || old == null) {
            return false
        }

        val cropFile = File(old.cropPath)

        val stagedCrop = if (crop == null) {
            null
        } else {
            stageCrop(cropFile, crop)
        }

        if (crop != null && stagedCrop == null) {
            return false
        }

        val cropBackup = backupFile(cropFile)
        val hadPreviousCrop = cropFile.exists()

        var previousIdentity: FaceFeatureIdentity? = null

        if (feature != null) {
            previousIdentity =
                InspireFace.FeatureHubGetFaceIdentity(id)

            if (
                previousIdentity == null ||
                !InspireFace.FeatureHubFaceUpdate(
                    FaceFeatureIdentity.create(id, feature)
                )
            ) {
                stagedCrop?.delete()
                return false
            }
        }

        if (
            stagedCrop != null &&
            !commitStagedCrop(
                cropFile,
                stagedCrop,
                cropBackup
            )
        ) {
            rollbackFeature(previousIdentity)
            return false
        }

        val updated = FaceRecord(
            id = id,
            name = normalizedName(name, id),
            cropPath = cropFile.absolutePath,
            updatedAt = System.currentTimeMillis()
        )

        val saved = metadata.edit()
            .putString(key(id), encode(updated))
            .commit()

        if (!saved) {
            if (stagedCrop != null) {
                restoreCrop(
                    cropFile,
                    cropBackup,
                    hadPreviousCrop
                )
            }

            rollbackFeature(previousIdentity)
            return false
        }

        cropBackup.delete()

        return true
    }

    fun delete(id: Long): Boolean {
        val record = get(id)

        if (!hubAcquired || record == null) {
            return false
        }

        val crop = File(record.cropPath)

        val pendingDelete = File(
            crop.parentFile,
            "${crop.name}.delete"
        )

        if (!recoverPendingDelete(crop, pendingDelete)) {
            return false
        }

        val hadCrop = crop.exists()

        if (hadCrop && !crop.renameTo(pendingDelete)) {
            return false
        }

        val previousIdentity =
            InspireFace.FeatureHubGetFaceIdentity(id)

        if (
            previousIdentity != null &&
            !InspireFace.FeatureHubFaceRemove(id) &&
            InspireFace.FeatureHubGetFaceIdentity(id) != null
        ) {
            restorePendingDelete(
                crop,
                pendingDelete,
                hadCrop
            )
            return false
        }

        val metadataRemoved =
            metadata.edit()
                .remove(key(id))
                .commit()

        if (!metadataRemoved) {
            if (
                previousIdentity != null &&
                InspireFace.FeatureHubGetFaceIdentity(id) == null
            ) {
                InspireFace.FeatureHubInsertFeature(
                    previousIdentity
                )
            }

            restorePendingDelete(
                crop,
                pendingDelete,
                hadCrop
            )

            return false
        }

        // The identity is already logically deleted. A leftover tombstone is harmless and
        // will be cleaned the next time this path is touched.
        pendingDelete.delete()

        return true
    }

    fun databaseFile(): File {
        return databaseFile
    }

    fun cropDirectory(): File {
        return cropDirectory
    }

    private fun cropFile(id: Long): File {
        return File(
            cropDirectory,
            "$id.jpg"
        )
    }

    private fun stageCrop(
        destination: File,
        crop: Bitmap
    ): File? {
        val parent = destination.parentFile

        if (
            parent == null ||
            (!parent.exists() && !parent.mkdirs())
        ) {
            return null
        }

        val backup = backupFile(destination)

        if (!recoverCropReplacement(destination, backup)) {
            return null
        }

        val temp = File(
            parent,
            "${destination.name}.tmp"
        )

        if (temp.exists() && !temp.delete()) {
            return null
        }

        try {
            FileOutputStream(temp).use { output ->
                if (
                    !crop.compress(
                        Bitmap.CompressFormat.JPEG,
                        92,
                        output
                    )
                ) {
                    temp.delete()
                    return null
                }

                output.flush()
                output.fd.sync()
            }
        } catch (_: IOException) {
            temp.delete()
            return null
        }

        return temp
    }

    private fun commitStagedCrop(
        destination: File,
        staged: File,
        backup: File
    ): Boolean {
        if (backup.exists() && !backup.delete()) {
            return false
        }

        val hadDestination = destination.exists()

        if (
            hadDestination &&
            !destination.renameTo(backup)
        ) {
            staged.delete()
            return false
        }

        if (!staged.renameTo(destination)) {
            if (hadDestination) {
                backup.renameTo(destination)
            }

            staged.delete()
            return false
        }

        return true
    }

    private fun restoreCrop(
        destination: File,
        backup: File,
        hadPreviousCrop: Boolean
    ) {
        if (destination.exists()) {
            destination.delete()
        }

        if (hadPreviousCrop && backup.exists()) {
            backup.renameTo(destination)
        } else {
            backup.delete()
        }
    }

    private fun recoverCropReplacement(
        destination: File,
        backup: File
    ): Boolean {
        if (!backup.exists()) {
            return true
        }

        if (destination.exists()) {
            return backup.delete()
        }

        return backup.renameTo(destination)
    }

    private fun recoverPendingDelete(
        crop: File,
        pendingDelete: File
    ): Boolean {
        if (!pendingDelete.exists()) {
            return true
        }

        if (crop.exists()) {
            return pendingDelete.delete()
        }

        return pendingDelete.renameTo(crop)
    }

    private fun restorePendingDelete(
        crop: File,
        pendingDelete: File,
        hadCrop: Boolean
    ) {
        if (hadCrop && pendingDelete.exists()) {
            pendingDelete.renameTo(crop)
        } else if (!hadCrop) {
            pendingDelete.delete()
        }
    }

    private fun rollbackFeature(
        previous: FaceFeatureIdentity?
    ) {
        if (previous != null) {
            InspireFace.FeatureHubFaceUpdate(previous)
        }
    }

    companion object {
        private const val KEY_PREFIX = "record."
        private const val KEY_NEXT_ID = "next_id"

        private val HUB_LOCK = Any()

        private var activeDatabasePath: String? = null
        private var hubReferences = 0

        private fun backupFile(destination: File): File {
            return File(
                destination.parentFile,
                "${destination.name}.bak"
            )
        }

        private fun normalizedName(
            name: String?,
            id: Long
        ): String {
            val trimmed = name?.trim() ?: ""

            return if (trimmed.isEmpty()) {
                "Face $id"
            } else {
                trimmed
            }
        }

        private fun key(id: Long): String {
            return "$KEY_PREFIX$id"
        }

        private fun encode(record: FaceRecord): String {
            return try {
                JSONObject()
                    .put("id", record.id)
                    .put("name", record.name)
                    .put("crop", record.cropPath)
                    .put("updated", record.updatedAt)
                    .toString()
            } catch (impossible: JSONException) {
                throw IllegalStateException(impossible)
            }
        }

        private fun decode(value: String?): FaceRecord? {
            if (value == null) {
                return null
            }

            return try {
                val json = JSONObject(value)

                FaceRecord(
                    json.getLong("id"),
                    json.getString("name"),
                    json.getString("crop"),
                    json.getLong("updated")
                )
            } catch (_: JSONException) {
                null
            }
        }
    }
}