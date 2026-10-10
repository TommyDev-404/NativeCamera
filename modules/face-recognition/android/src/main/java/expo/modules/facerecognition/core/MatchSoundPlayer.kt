
package expo.modules.facerecognition.core

import expo.modules.facerecognition.R
import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class MatchSoundPlayer(context: Context) {
    private val soundPool: SoundPool
    private val soundId: Int
    private var isLoaded = false

    init {
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(attributes)
            .build()

        soundPool.setOnLoadCompleteListener { _, _, status ->
            isLoaded = status == 0
        }

        soundId = soundPool.load(context, R.raw.success_sound, 1)
    }

    fun play() {
        if (isLoaded) {
            soundPool.play(soundId, 1f, 1f, 1, 0, 1f)
        }
    }

    fun release() {
        soundPool.release()
    }
}
