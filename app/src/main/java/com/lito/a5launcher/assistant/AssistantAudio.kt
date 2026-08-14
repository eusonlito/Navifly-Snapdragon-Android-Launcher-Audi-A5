package com.lito.a5launcher.assistant

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.lito.a5launcher.R
import java.io.ByteArrayOutputStream
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.abs

internal class AssistantAudio(private val context: Context) {
    private var recorder: AudioRecord? = null
    private var recordingToken: AtomicBoolean? = null
    private var captureThread: Thread? = null
    private val playback = ReusableAudioPlayback(::createPlaybackHandle)

    @SuppressLint("MissingPermission")
    fun recordClosedTurn(
        sampleRate: Int,
        onChunk: (ByteArray) -> Unit,
        onLevel: (Int) -> Unit,
        onFinished: (SpeechCaptureResult) -> Unit,
        onFailure: (String) -> Unit,
    ) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            onFailure(context.getString(R.string.assistant_error_microphone_permission))
            return
        }
        stopRecording()
        val minimum = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
        )
        val localRecorder = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, CHUNK_BYTES * 2),
        )
        if (localRecorder.state != AudioRecord.STATE_INITIALIZED) {
            localRecorder.release()
            onFailure(context.getString(R.string.assistant_error_microphone_unavailable))
            return
        }
        val noiseSuppressor = if (NoiseSuppressor.isAvailable()) {
            runCatching {
                NoiseSuppressor.create(localRecorder.audioSessionId)?.apply { enabled = true }
            }.getOrNull()
        } else {
            null
        }
        val localToken = AtomicBoolean(true)
        synchronized(this) {
            recorder = localRecorder
            recordingToken = localToken
        }
        localRecorder.startRecording()
        val localThread = thread(start = false, name = "assistant-audio-capture", isDaemon = true) {
            val buffer = ByteArray(CHUNK_BYTES)
            val startedAt = SystemClock.elapsedRealtime()
            val speechWindow = SpeechCaptureWindow()
            var result = SpeechCaptureResult.CANCELLED
            try {
                while (localToken.get()) {
                    val count = localRecorder.read(buffer, 0, buffer.size)
                    if (count <= 0) continue
                    val chunk = buffer.copyOf(count)
                    onChunk(chunk)
                    val amplitude = averageAmplitude(chunk)
                    onLevel(amplitude)
                    when (
                        speechWindow.onAudioLevel(
                            amplitude,
                            SystemClock.elapsedRealtime() - startedAt,
                        )
                    ) {
                        SpeechCaptureDecision.CONTINUE -> Unit
                        SpeechCaptureDecision.SPEECH_COMPLETE -> {
                            result = SpeechCaptureResult.SPEECH
                            break
                        }
                        SpeechCaptureDecision.NO_SPEECH -> {
                            result = SpeechCaptureResult.NO_SPEECH
                            break
                        }
                    }
                }
            } finally {
                localToken.set(false)
                runCatching { localRecorder.stop() }
                noiseSuppressor?.release()
                localRecorder.release()
                synchronized(this@AssistantAudio) {
                    if (recorder === localRecorder) recorder = null
                    if (recordingToken === localToken) recordingToken = null
                    if (captureThread === Thread.currentThread()) captureThread = null
                }
                onFinished(result)
            }
        }
        synchronized(this) { captureThread = localThread }
        localThread.start()
    }

    fun stopRecording() {
        val localRecorder: AudioRecord?
        val localThread: Thread?
        synchronized(this) {
            recordingToken?.set(false)
            localRecorder = recorder
            localThread = captureThread
        }
        runCatching { localRecorder?.stop() }
        if (localThread != null && localThread !== Thread.currentThread()) {
            runCatching { localThread.join(STOP_JOIN_MS) }
        }
    }

    @Synchronized
    fun play(pcm16: ByteArray, sampleRate: Int): Boolean = playback.play(pcm16, sampleRate)

    @Synchronized
    fun repeat(): Boolean = playback.repeat()

    private fun createPlaybackHandle(pcm16: ByteArray, sampleRate: Int): PcmPlaybackHandle? {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm16.size)
            .build()
        val written = track.write(pcm16, 0, pcm16.size)
        if (written != pcm16.size) {
            track.release()
            return null
        }
        return AudioTrackPlaybackHandle(track)
    }

    @Synchronized
    fun stopPlayback() {
        playback.release()
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }

    private fun averageAmplitude(bytes: ByteArray): Int {
        if (bytes.size < 2) return 0
        var sum = 0L
        var samples = 0
        var index = 0
        while (index + 1 < bytes.size) {
            val sample = ((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xff)).toShort()
            sum += abs(sample.toInt())
            samples++
            index += 2
        }
        return if (samples == 0) 0 else (sum / samples).toInt()
    }

    private companion object {
        const val CHUNK_BYTES = 3_200
        const val STOP_JOIN_MS = 500L
    }
}

internal interface PcmPlaybackHandle {
    fun start(): Boolean
    fun repeat(): Boolean
    fun release()
}

internal class ReusableAudioPlayback(
    private val createHandle: (ByteArray, Int) -> PcmPlaybackHandle?,
) {
    private var handle: PcmPlaybackHandle? = null

    fun play(pcm16: ByteArray, sampleRate: Int): Boolean {
        release()
        if (pcm16.isEmpty()) return false
        val created = createHandle(pcm16, sampleRate) ?: return false
        if (!created.start()) {
            created.release()
            return false
        }
        handle = created
        return true
    }

    fun repeat(): Boolean {
        val current = handle ?: return false
        if (current.repeat()) return true
        release()
        return false
    }

    fun release() {
        handle?.release()
        handle = null
    }
}

private class AudioTrackPlaybackHandle(private val track: AudioTrack) : PcmPlaybackHandle {
    override fun start(): Boolean = runCatching {
        track.play()
        true
    }.getOrDefault(false)

    override fun repeat(): Boolean = runCatching {
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
        if (track.reloadStaticData() == AudioTrack.SUCCESS) {
            track.play()
            true
        } else {
            false
        }
    }.getOrDefault(false)

    override fun release() {
        runCatching { track.stop() }
        track.release()
    }
}

internal enum class SpeechCaptureResult { SPEECH, NO_SPEECH, CANCELLED }

internal enum class SpeechCaptureDecision { CONTINUE, SPEECH_COMPLETE, NO_SPEECH }

internal class SpeechCaptureWindow {
    private var speechDetected = false
    private var silentSinceMs: Long? = null

    fun onAudioLevel(amplitude: Int, elapsedMs: Long): SpeechCaptureDecision {
        if (amplitude >= SPEECH_AMPLITUDE) {
            speechDetected = true
            silentSinceMs = null
        } else if (speechDetected) {
            val silenceStart = silentSinceMs ?: elapsedMs.also { silentSinceMs = it }
            if (elapsedMs - silenceStart >= END_SILENCE_MS) {
                return SpeechCaptureDecision.SPEECH_COMPLETE
            }
        }
        if (!speechDetected && elapsedMs >= INITIAL_SPEECH_TIMEOUT_MS) {
            return SpeechCaptureDecision.NO_SPEECH
        }
        if (elapsedMs >= MAX_TURN_MS) {
            return if (speechDetected) {
                SpeechCaptureDecision.SPEECH_COMPLETE
            } else {
                SpeechCaptureDecision.NO_SPEECH
            }
        }
        return SpeechCaptureDecision.CONTINUE
    }

    private companion object {
        const val SPEECH_AMPLITUDE = 450
        const val END_SILENCE_MS = 900L
        const val INITIAL_SPEECH_TIMEOUT_MS = 5_000L
        const val MAX_TURN_MS = 12_000L
    }
}

internal class PcmAccumulator {
    private val output = ByteArrayOutputStream()
    fun append(bytes: ByteArray) = output.write(bytes)
    fun bytes(): ByteArray = output.toByteArray()
    fun reset() = output.reset()
}
