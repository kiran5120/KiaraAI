package com.kiara.ai.voice

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
import java.util.Locale

/** Speech-to-text (Android SpeechRecognizer) + text-to-speech (system TTS engine). Call from the main thread. */
class VoiceManager(private val ctx: Context, private val cb: Callback) {
    interface Callback {
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onListeningChanged(listening: Boolean)
        fun onSpeakingChanged(speaking: Boolean)
        fun onLevel(level: Float)
        fun onVoiceError(message: String, transient: Boolean)
        fun onSpeechDone()
    }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var ttsReady = false
    var listening = false
        private set

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) { main.post { cb.onSpeakingChanged(true) } }
                override fun onDone(utteranceId: String?) { main.post { cb.onSpeakingChanged(false); cb.onSpeechDone() } }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) { main.post { cb.onSpeakingChanged(false); cb.onSpeechDone() } }
            })
        }
    }

    fun isRecognitionAvailable() = SpeechRecognizer.isRecognitionAvailable(ctx)

    fun startListening(localeTag: String) {
        if (!isRecognitionAvailable()) {
            cb.onVoiceError("Speech recognition isn't available on this phone (install/enable the Google app).", false)
            return
        }
        stopSpeaking()
        recognizer?.destroy()
        recognizer = SpeechRecognizer.createSpeechRecognizer(ctx).also { sr ->
            sr.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { setListening(true) }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) { cb.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f)) }
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() { setListening(false) }
                override fun onError(error: Int) {
                    setListening(false)
                    when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                            cb.onVoiceError("I didn't catch that.", true)
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                            cb.onVoiceError("Microphone permission is needed.", false)
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT ->
                            cb.onVoiceError("Voice recognition needs a connection for this language.", false)
                        SpeechRecognizer.ERROR_CLIENT -> {}
                        else -> cb.onVoiceError("Voice recognition error ($error).", false)
                    }
                }
                override fun onResults(results: Bundle?) {
                    setListening(false)
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
                    if (text.isBlank()) cb.onVoiceError("I didn't catch that.", true) else cb.onFinal(text)
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { cb.onPartial(it) }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, localeTag)
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            sr.startListening(i)
        }
    }

    fun stopListening() {
        recognizer?.stopListening()
        recognizer?.cancel()
        setListening(false)
    }

    private fun setListening(v: Boolean) {
        if (listening != v) { listening = v; cb.onListeningChanged(v) }
    }

    fun speak(text: String, pitch: Float, rate: Float) {
        val engine = tts ?: return
        if (!ttsReady || text.isBlank()) { cb.onSpeechDone(); return }
        val loc = when {
            text.any { it.code in 0x0900..0x097F } -> Locale("hi", "IN")
            text.any { it.code in 0x0A80..0x0AFF } -> Locale("gu", "IN")
            else -> Locale("en", "IN")
        }
        var res = engine.setLanguage(loc)
        if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
            res = engine.setLanguage(Locale.ENGLISH)
        }
        // Prefer a voice the engine labels as female, if it exposes one for this language.
        try {
            engine.voices?.filter { it.locale.language == engine.language?.language && !it.isNetworkConnectionRequired }
                ?.firstOrNull { it.name.contains("female", ignoreCase = true) }
                ?.let { engine.voice = it }
        } catch (_: Exception) {}
        engine.setPitch(pitch)
        engine.setSpeechRate(rate)
        engine.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "kiara-${System.nanoTime()}")
    }

    fun stopSpeaking() {
        tts?.stop()
        cb.onSpeakingChanged(false)
    }

    fun shutdown() {
        recognizer?.destroy(); recognizer = null
        tts?.stop(); tts?.shutdown(); tts = null
    }
}
