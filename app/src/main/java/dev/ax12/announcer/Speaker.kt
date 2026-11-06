package dev.ax12.announcer

import android.content.Context
import android.media.AudioAttributes
import android.os.SystemClock
import android.speech.tts.TextToSpeech
import java.util.ArrayDeque
import java.util.Locale

/** Android text-to-speech with a start-up queue and duplicate suppression. */
class Speaker(context: Context) : Announcer {

    private var tts: TextToSpeech? = null
    private var ready = false
    private val pendingText = ArrayDeque<String>()
    private val pendingUrgent = ArrayDeque<Boolean>()
    private var lastText = ""
    private var lastTime = 0L

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                engine.setLanguage(Locale.getDefault())
                engine.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                ready = true
                while (pendingText.isNotEmpty()) {
                    speakNow(pendingText.removeFirst(), pendingUrgent.removeFirst())
                }
            }
        }
    }

    override fun say(text: String, urgent: Boolean) {
        val now = SystemClock.elapsedRealtime()
        if (text == lastText && now - lastTime < 700) return   // same words twice in a row
        lastText = text
        lastTime = now
        if (!ready) {
            pendingText.addLast(text)
            pendingUrgent.addLast(urgent)
            return
        }
        speakNow(text, urgent)
    }

    private fun speakNow(text: String, urgent: Boolean) {
        val mode = if (urgent) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
        tts?.speak(text, mode, null, "ax12-" + text.hashCode())
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        tts = null
    }
}
