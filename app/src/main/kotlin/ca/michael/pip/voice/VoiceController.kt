package ca.michael.pip.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import ca.michael.pip.PipSettings
import ca.michael.pip.R
import ca.michael.pip.core.text.SpeechText
import ca.michael.pip.core.text.VoiceOption
import ca.michael.pip.core.text.VoicePick
import java.util.Locale

/**
 * Voice in and out (V1-2). The mic opens only when the owner presses Talk
 * (FR-2.1). On-device recognition first; the system recognizer only after
 * the owner's one-time opt-in (FR-2.2). Pip never records or stores audio:
 * only the recognized text comes back (FR-2.3).
 */
class VoiceController(private val context: Context, private val settings: PipSettings, private val listener: Listener) {

    interface Listener {
        fun onListening()
        fun onHeard(text: String)
        fun onVoiceError(messageRes: Int)
        fun onSpeaking()
        fun onSpeakingDone()
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var ttsReady = false
    private val tts = TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }

    fun startListening(): Boolean {
        cancelListening()
        val r = when {
            // UNKNOWN: availability semantics checked against
            // https://developer.android.com/reference/android/speech/SpeechRecognizer#isOnDeviceRecognitionAvailable(android.content.Context)
            SpeechRecognizer.isOnDeviceRecognitionAvailable(context) -> SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
            settings.systemAsrOptIn && SpeechRecognizer.isRecognitionAvailable(context) -> SpeechRecognizer.createSpeechRecognizer(context)
            else -> {
                listener.onVoiceError(R.string.err_asr_missing)
                return false
            }
        }
        recognizer = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) = listener.onListening()
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}
            override fun onError(error: Int) {
                cancelListening()
                listener.onVoiceError(R.string.err_asr_failed)
            }
            override fun onResults(results: Bundle?) {
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()
                cancelListening()
                if (text.isNullOrBlank()) listener.onVoiceError(R.string.err_asr_failed) else listener.onHeard(text)
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, settings.speechLanguage)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true))
        return true
    }

    fun stopListening() {
        recognizer?.stopListening()
    }

    fun cancelListening() {
        recognizer?.cancel()
        recognizer?.destroy()
        recognizer = null
    }

    /** FR-2.4: system TTS, French by default, English when the owner spoke English. */
    fun speak(text: String, english: Boolean) {
        if (!ttsReady) {
            listener.onSpeakingDone()
            return
        }
        useBestVoice(if (english) Locale.CANADA else Locale.CANADA_FRENCH)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { main.post { listener.onSpeaking() } }
            override fun onDone(utteranceId: String?) { main.post { listener.onSpeakingDone() } }
            @Deprecated("Required override")
            override fun onError(utteranceId: String?) { main.post { listener.onSpeakingDone() } }
            override fun onStop(utteranceId: String?, interrupted: Boolean) { main.post { listener.onSpeakingDone() } }
        })
        tts.speak(SpeechText.clean(text, english), TextToSpeech.QUEUE_FLUSH, null, "pip-answer")
    }

    /**
     * The engine's default voice for a locale is often its plainest one. Pick the
     * best installed voice for the region (or the language) instead; keep the
     * default when the engine lists none.
     */
    private fun useBestVoice(locale: Locale) {
        val voices = try { tts.voices.orEmpty() } catch (e: RuntimeException) { emptySet() }
        val options = voices.map {
            VoiceOption(
                name = it.name,
                localeTag = it.locale.toLanguageTag(),
                quality = it.quality,
                needsNetwork = it.isNetworkConnectionRequired,
                installed = TextToSpeech.Engine.KEY_FEATURE_NOT_INSTALLED !in it.features.orEmpty(),
            )
        }
        val pick = VoicePick.best(options, locale.toLanguageTag())
        val voice = pick?.let { p -> voices.firstOrNull { it.name == p.name } }
        if (voice == null || tts.setVoice(voice) != TextToSpeech.SUCCESS) tts.language = locale
    }

    fun stopSpeaking() {
        tts.stop()
    }

    fun release() {
        cancelListening()
        tts.stop()
        tts.shutdown()
    }
}
