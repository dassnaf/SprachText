package de.kdvstudio.sprachtext

import android.content.Context
import android.content.Intent
import android.media.AudioFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Erkennt die Abschnitte nacheinander mit Androids Offline-Spracherkennung. */
class Transcriber(private val ctx: Context, private val cb: Callback) {

    interface Callback {
        fun onProgress(done: Int, total: Int)
        fun onDone(segments: List<String>)
        fun onError(msg: String)
    }

    companion object {
        const val LANGUAGE = "de-DE"
        fun languageIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE, LANGUAGE)
    }

    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    private var readFd: ParcelFileDescriptor? = null
    private var chunks: List<ShortArray> = emptyList()
    private var index = 0
    private var retried = false
    private val segments = ArrayList<String>()
    private var finished = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onPartialResults(partialResults: Bundle?) {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
                ?.let { segments.add(it) }
            advance()
        }

        override fun onError(error: Int) {
            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> advance()
                SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> {
                    rec?.triggerModelDownload(languageIntent())
                    fail("Das deutsche Offline-Sprachpaket fehlt. Der Download wurde gestartet – bitte in ein paar Minuten nochmal versuchen.")
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS ->
                    fail("Mikrofon-Berechtigung fehlt (öffne die App einmal und erteile sie).")
                else -> {
                    if (!retried) { retried = true; index--; main.postDelayed({ next() }, 400) }
                    else advance()
                }
            }
        }
    }

    fun start(pcm: ShortArray) {
        if (!SpeechRecognizer.isOnDeviceRecognitionAvailable(ctx)) {
            fail("Auf diesem Gerät ist keine Offline-Spracherkennung verfügbar."); return
        }
        chunks = Chunker.split(pcm)
        if (chunks.isEmpty()) { fail("Keine Sprache erkannt."); return }
        rec = SpeechRecognizer.createOnDeviceSpeechRecognizer(ctx).apply { setRecognitionListener(listener) }
        next()
    }

    private fun advance() {
        retried = false
        main.postDelayed({ next() }, 150)
    }

    private fun next() {
        if (finished) return
        closeFd()
        if (index >= chunks.size) { finish(); return }
        cb.onProgress(index + 1, chunks.size)
        val chunk = chunks[index]
        index++
        val pipe = ParcelFileDescriptor.createPipe()
        readFd = pipe[0]
        val intent = languageIntent()
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, pipe[0])
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1)
            .putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, AudioDecoder.TARGET_RATE)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
            .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 3000L)
        rec?.startListening(intent)
        Thread { writePcm(chunk, pipe[1]) }.start()
    }

    private fun writePcm(pcm: ShortArray, fd: ParcelFileDescriptor) {
        try {
            ParcelFileDescriptor.AutoCloseOutputStream(fd).use { os ->
                val bb = ByteBuffer.allocate(pcm.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                bb.asShortBuffer().put(pcm)
                val bytes = bb.array()
                var off = 0
                while (off < bytes.size) {
                    val n = minOf(8192, bytes.size - off)
                    os.write(bytes, off, n)
                    off += n
                }
            }
        } catch (e: IOException) {
            // Erkennung für diesen Abschnitt beendet
        }
    }

    private fun closeFd() {
        try { readFd?.close() } catch (e: IOException) {}
        readFd = null
    }

    private fun finish() {
        if (finished) return
        finished = true
        if (segments.isEmpty()) cb.onError("Keine Sprache erkannt.") else cb.onDone(segments.toList())
        destroy()
    }

    private fun fail(msg: String) {
        if (finished) return
        finished = true
        cb.onError(msg)
        destroy()
    }

    fun destroy() {
        finished = true
        main.removeCallbacksAndMessages(null)
        rec?.destroy(); rec = null
        closeFd()
    }
}
