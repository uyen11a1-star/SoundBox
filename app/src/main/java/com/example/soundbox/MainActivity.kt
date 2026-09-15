package com.example.soundbox

import android.content.ContentValues
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var engine: AudioEngine
    private lateinit var statusText: TextView
    private lateinit var btnRecord: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = AudioEngine()

        val piano = findViewById<PianoView>(R.id.piano)
        piano.onNoteOn = { midi -> engine.noteOn(midi) }
        piano.onNoteOff = { midi -> engine.noteOff(midi) }

        statusText = findViewById(R.id.statusText)
        btnRecord = findViewById(R.id.btnRecord)
        val btnPlay = findViewById<Button>(R.id.btnPlay)
        val btnExport = findViewById<Button>(R.id.btnExport)

        btnRecord.setOnClickListener {
            if (engine.isRecording) {
                engine.stopRecording()
                btnRecord.text = "🔴 Ghi"
                statusText.text = "Đã dừng. Bấm Phát để nghe lại."
            } else {
                engine.startRecording()
                btnRecord.text = "⏹ Dừng"
                statusText.text = "Đang ghi... đánh đàn đi!"
            }
        }

        btnPlay.setOnClickListener {
            if (engine.getRecordedSamples().isEmpty()) {
                Toast.makeText(this, "Chưa có bản ghi nào", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            statusText.text = "Đang phát lại..."
            engine.playRecording {
                runOnUiThread { statusText.text = "Phát xong." }
            }
        }

        btnExport.setOnClickListener { exportWav() }
    }

    private fun exportWav() {
        val samples = engine.getRecordedSamples()
        if (samples.isEmpty()) {
            Toast.makeText(this, "Chưa có bản ghi nào", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val name = "SoundBox_" +
                SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".wav"
            val savedPath: String

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, name)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
                    put(MediaStore.Audio.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_MUSIC + "/SoundBox")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = contentResolver.insert(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw RuntimeException("Khong tao duoc MediaStore entry")
                contentResolver.openOutputStream(uri)?.use { out ->
                    val tmp = File(cacheDir, name)
                    WavWriter.write(samples, AudioEngine.SAMPLE_RATE, tmp)
                    tmp.inputStream().use { it.copyTo(out) }
                    tmp.delete()
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                contentResolver.update(uri, values, null, null)
                savedPath = "Music/SoundBox/$name"
            } else {
                val dir = File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "SoundBox")
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, name)
                WavWriter.write(samples, AudioEngine.SAMPLE_RATE, file)
                savedPath = file.absolutePath
            }

            statusText.text = "Da luu: $savedPath"
            Toast.makeText(this, "Xuat WAV thanh cong", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            statusText.text = "Loi xuat file: ${e.message}"
            Toast.makeText(this, "Loi: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }
}
