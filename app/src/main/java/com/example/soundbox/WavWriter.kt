package com.example.soundbox

import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Ghi file WAV chuan RIFF (PCM 16-bit mono).
 * Header 44 bytes, sau do la raw PCM samples.
 */
object WavWriter {

    fun write(samples: ShortArray, sampleRate: Int, outFile: File) {
        val dataSize = samples.size * 2       // 16-bit = 2 bytes/sample
        val totalSize = 36 + dataSize

        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(totalSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)                     // fmt chunk size
        header.putShort(1)                    // PCM = 1
        header.putShort(1)                    // mono = 1 channel
        header.putInt(sampleRate)
        header.putInt(sampleRate * 2)         // byte rate = sampleRate * channels * bytes/sample
        header.putShort(2)                    // block align
        header.putShort(16)                   // bits per sample
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)

        FileOutputStream(outFile).use { fos ->
            fos.write(header.array())
            val body = ByteBuffer.allocate(dataSize).order(ByteOrder.LITTLE_ENDIAN)
            for (s in samples) body.putShort(s)
            fos.write(body.array())
        }
    }
}
