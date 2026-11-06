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
    @Volatile var muted = false
    private var lastText = ""
    private var lastTime = 0L
    private var locale: Locale = Locale.getDefault()
    /** The result of the last setLanguage: TextToSpeech.LANG_AVAILABLE or better means the voice exists. */
    @Volatile var languageResult: Int = TextToSpeech.LANG_AVAILABLE

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            val engine = tts
            if (status == TextToSpeech.SUCCESS && engine != null) {
                languageResult = engine.setLanguage(locale)
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

    /** The language the voice speaks. */
    fun setLanguage(l: Locale) {
        locale = l
        val engine = tts
        if (ready && engine != null) languageResult = engine.setLanguage(l)
    }

    /** False when Android has no voice for the chosen language (install it in Settings, Text-to-speech). */
    fun languageOk(): Boolean = languageResult >= TextToSpeech.LANG_AVAILABLE

    override fun say(text: String, urgent: Boolean) {
        if (muted) return
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
