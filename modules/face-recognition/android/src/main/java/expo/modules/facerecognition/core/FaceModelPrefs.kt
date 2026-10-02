package expo.modules.facerecognition.core

import android.content.Context

/**
 * Process-wide model selection shared by the home screen and all feature screens.
 */
object FaceModelPrefs {
    enum class Model(val sdkName: String) {
        PIKACHU("Pikachu"),
        MEGATRON("Megatron");

        companion object {
            fun fromStored(value: String?): Model {
                for (model in entries) {
                    if (model.sdkName == value) {
                        return model
                    }
                }
                return MEGATRON
            }
        }
    }

    private const val PREFS = "settings"
    private const val KEY_MODEL = "face_model"

    /**
     * Megatron remains the default to preserve the project's previous behavior.
     */
    fun get(context: Context): Model {
        val value = context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).getString(
            KEY_MODEL,
            Model.MEGATRON.sdkName
        )

        return Model.fromStored(value)
    }

    fun set(context: Context, model: Model) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        ).edit()
            .putString(KEY_MODEL, model.sdkName)
            .apply()
    }
}