package com.example.soundbox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentHashMap

/**
 * Cong thuc tan so not nhac (chuan MIDI):
 *     f = 440 * 2^((n - 69) / 12)
 * Trong do 440 Hz = A4 (La4), 69 = so MIDI cua A4.
 * Vi du: C4 = MIDI 60 -> 261.63 Hz
 */
fun midiToFrequency(midi: Int): Double =
    440.0 * Math.pow(2.0, (midi - 69) / 12.0)

class AudioEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        private const val ATTACK = 0.01      // giay
        private const val RELEASE = 0.25     // giay
        private const val MAX_VOLUME = 0.7
        private const val MAX_RECORD_SECONDS = 300
    }

    private class Voice(val frequency: Double) {
        var phase = 0.0
        var released = false
        var releaseLevel = 1.0
        var timeAlive = 0.0
    }

    private val voices = ConcurrentHashMap<Int, Voice>()
    private val audioTrack: AudioTrack
    @Volatile private var running = true
    private val renderThread: Thread

    // ---- Recording buffer ----
    @Volatile var isRecording = false
        private set
    private val recordLock = Any()
    private val recordArray = ShortArray(SAMPLE_RATE * MAX_RECORD_SECONDS)
    private var recordPos = 0

    init {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val targetBuf = SAMPLE_RATE / 10 * 2   // ~100ms
        val bufSize = if (minBuf > 0) maxOf(minBuf, targetBuf) else SAMPLE_RATE / 5 * 2

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack.play()

        renderThread = Thread { renderLoop() }.apply {
            priority = Thread.MAX_PRIORITY
            start()
        }
    }

    fun noteOn(midi: Int) {
        // Tao voice moi (retrigger neu da co)
        voices[midi] = Voice(midiToFrequency(midi))
    }

    fun noteOff(midi: Int) {
        voices[midi]?.released = true
    }

    private fun renderLoop() {
        val blockSize = 512
        val buffer = ShortArray(blockSize)
        val twoPi = 2.0 * Math.PI

        while (running) {
            var activeCount = 0

            for (i in 0 until blockSize) {
                var sample = 0.0
                var n = 0

                for (v in voices.values) {
                    if (v.released && v.releaseLevel <= 0.0) continue

                    // Envelope
                    var env = 1.0
                    if (v.timeAlive < ATTACK) {
                        env = v.timeAlive / ATTACK
                    }
                    if (v.released) {
                        v.releaseLevel -= 1.0 / (RELEASE * SAMPLE_RATE)
                        if (v.releaseLevel <= 0.0) {
                            v.releaseLevel = 0.0
                            continue
                        }
                        env *= v.releaseLevel
                    }

                    // Sinh song sine bang phase accumulator
                    sample += Math.sin(v.phase) * env
                    v.phase += twoPi * v.frequency / SAMPLE_RATE
                    if (v.phase > twoPi) v.phase -= twoPi
                    v.timeAlive += 1.0 / SAMPLE_RATE
                    n++
                }

                if (n > 0) {
                    // Equal-power mix (chia sqrt) -> am luong on dinh
                    sample = sample / Math.sqrt(n.toDouble())
                }
                sample = sample.coerceIn(-1.0, 1.0)
                buffer[i] = (sample * MAX_VOLUME * Short.MAX_VALUE).toInt().toShort()
                activeCount = n
            }

            // Don sach voice da tat
            val dead = ArrayList<Int>(4)
            for ((k, v) in voices) {
                if (v.released && v.releaseLevel <= 0.0) dead.add(k)
            }
            for (k in dead) voices.remove(k)

            audioTrack.write(buffer, 0, blockSize)

            // Ghi am
            if (isRecording) {
                synchronized(recordLock) {
                    if (recordPos + blockSize <= recordArray.size) {
                        System.arraycopy(buffer, 0, recordArray, recordPos, blockSize)
                        recordPos += blockSize
                    } else {
                        isRecording = false
                    }
                }
            }
        }
    }

    fun startRecording() {
        synchronized(recordLock) { recordPos = 0 }
        isRecording = true
    }

    fun stopRecording() {
        isRecording = false
    }

    fun getRecordedSamples(): ShortArray {
        synchronized(recordLock) {
            return recordArray.copyOf(recordPos)
        }
    }

    fun playRecording(onDone: () -> Unit) {
        val data = getRecordedSamples()
        if (data.isEmpty()) { onDone(); return }

        Thread {
            val bufSize = SAMPLE_RATE / 5 * 2
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()
            track.write(data, 0, data.size)
            // Cho audio con lai trong buffer phat het
            Thread.sleep(400)
            try { track.stop(); track.release() } catch (_: Exception) {}
            onDone()
        }.start()
    }

    fun release() {
        running = false
        try { renderThread.join(500) } catch (_: Exception) {}
        try { audioTrack.stop(); audioTrack.release() } catch (_: Exception) {}
    }
}
