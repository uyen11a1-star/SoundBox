package com.example.soundbox

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

/**
 * Synth 4 loai trong bang code, khong dung file am thanh:
 *   KICK : sine sweep 150 -> 40Hz, envelope exp decay
 *   SNARE: white noise + sine 200Hz
 *   HIHAT: white noise qua high-pass 1-pole, decay nhanh
 *   TOM  : sine 180 -> 100Hz
 * Cung cap step sequencer 16 buoc dong bo sample-accurate.
 */
class DrumEngine {

    companion object {
        const val SAMPLE_RATE = 44100
        const val KICK = 0
        const val SNARE = 1
        const val HIHAT = 2
        const val TOM = 3
        const val NUM_VOICES = 4
        const val NUM_STEPS = 16
    }

    private val audioTrack: AudioTrack
    @Volatile private var running = true
    private val renderThread: Thread

    @Volatile var pattern: Array<BooleanArray> =
        Array(NUM_VOICES) { BooleanArray(NUM_STEPS) }

    @Volatile var bpm: Int = 120
    @Volatile var isPlaying: Boolean = false
        private set
    @Volatile var currentStep: Int = -1
        private set

    var onStepChanged: ((Int) -> Unit)? = null

    private val pendingHits = ConcurrentLinkedQueue<Int>()
    private val activeVoices = ArrayList<DrumVoice>()

    private var sampleUntilNextStep = 0
    private var stepIndex = 0

    init {
        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufSize = if (minBuf > 0) maxOf(minBuf, SAMPLE_RATE / 5 * 2) else SAMPLE_RATE / 5 * 2

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

    fun trigger(voice: Int) = pendingHits.add(voice)

    fun start() {
        stepIndex = 0
        sampleUntilNextStep = 0
        isPlaying = true
    }

    fun stop() {
        isPlaying = false
        currentStep = -1
        onStepChanged?.invoke(-1)
    }

    fun toggleCell(voice: Int, step: Int) {
        pattern[voice][step] = !pattern[voice][step]
    }

    fun clearPattern() {
        pattern = Array(NUM_VOICES) { BooleanArray(NUM_STEPS) }
    }

    /** 16 buoc = 1 bar = 4 beat -> moi buoc = (60/bpm)/4 giay */
    private fun samplesPerStep(): Int {
        val b = bpm.coerceIn(40, 240)
        return (60.0 / b / 4.0 * SAMPLE_RATE).toInt()
    }

    private fun renderLoop() {
        val blockSize = 256
        val buffer = ShortArray(blockSize)

        while (running) {
            while (true) {
                val v = pendingHits.poll() ?: break
                activeVoices.add(createVoice(v))
            }

            for (i in 0 until blockSize) {
                if (isPlaying) {
                    if (sampleUntilNextStep <= 0) {
                        for (voice in 0 until NUM_VOICES) {
                            if (pattern[voice][stepIndex]) activeVoices.add(createVoice(voice))
                        }
                        currentStep = stepIndex
                        onStepChanged?.invoke(stepIndex)
                        stepIndex = (stepIndex + 1) % NUM_STEPS
                        sampleUntilNextStep = samplesPerStep()
                    }
                    sampleUntilNextStep--
                }

                var sample = 0.0
                var n = 0
                val it = activeVoices.iterator()
                while (it.hasNext()) {
                    val v = it.next()
                    val s = v.nextSample()
                    if (v.isDone()) it.remove() else { sample += s; n++ }
                }

                if (n > 0) sample /= Math.sqrt(n.toDouble())
                sample = sample.coerceIn(-1.0, 1.0)
                buffer[i] = (sample * 0.7 * Short.MAX_VALUE).toInt().toShort()
            }

            audioTrack.write(buffer, 0, blockSize)
        }
    }

    private fun createVoice(type: Int): DrumVoice = when (type) {
        KICK -> KickVoice()
        SNARE -> SnareVoice()
        HIHAT -> HihatVoice()
        TOM -> TomVoice()
        else -> KickVoice()
    }

    fun release() {
        running = false
        isPlaying = false
        try { renderThread.join(500) } catch (_: Exception) {}
        try { audioTrack.stop(); audioTrack.release() } catch (_: Exception) {}
    }

    // ---------- Voices ----------

    private abstract inner class DrumVoice {
        protected var t = 0.0
        abstract fun nextSample(): Double
        abstract fun isDone(): Boolean
    }

    private inner class KickVoice : DrumVoice() {
        private var phase = 0.0
        private val twoPi = 2.0 * Math.PI
        override fun nextSample(): Double {
            val freq = 40.0 + 110.0 * exp(-t * 30.0)
            val env = exp(-t * 18.0)
            val s = sin(phase) * env
            phase += twoPi * freq / SAMPLE_RATE
            t += 1.0 / SAMPLE_RATE
            return s * 0.95
        }
        override fun isDone() = t > 0.8
    }

    private inner class SnareVoice : DrumVoice() {
        private var phase = 0.0
        private val twoPi = 2.0 * Math.PI
        override fun nextSample(): Double {
            val noise = Random.nextDouble(-1.0, 1.0)
            val envN = exp(-t * 22.0)
            val envT = exp(-t * 18.0)
            val tone = sin(phase) * envT
            phase += twoPi * 200.0 / SAMPLE_RATE
            t += 1.0 / SAMPLE_RATE
            return (noise * 0.7 * envN + tone * 0.4 * envT)
                .coerceIn(-1.0, 1.0) * 0.85
        }
        override fun isDone() = t > 0.5
    }

    private inner class HihatVoice : DrumVoice() {
        private var prev = 0.0
        override fun nextSample(): Double {
            val noise = Random.nextDouble(-1.0, 1.0)
            val env = exp(-t * 60.0)
            val hp = noise - prev           // 1-pole high-pass don gian
            prev = noise
            t += 1.0 / SAMPLE_RATE
            return hp * env * 0.45
        }
        override fun isDone() = t > 0.3
    }

    private inner class TomVoice : DrumVoice() {
        private var phase = 0.0
        private val twoPi = 2.0 * Math.PI
        override fun nextSample(): Double {
            val freq = 100.0 + 80.0 * exp(-t * 12.0)
            val env = exp(-t * 8.0)
            val s = sin(phase) * env
            phase += twoPi * freq / SAMPLE_RATE
            t += 1.0 / SAMPLE_RATE
            return s * 0.85
        }
        override fun isDone() = t > 1.0
    }
}
