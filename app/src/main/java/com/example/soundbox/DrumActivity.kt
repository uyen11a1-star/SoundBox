package com.example.soundbox

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class DrumActivity : AppCompatActivity() {

    private lateinit var engine: DrumEngine
    private lateinit var padView: DrumPadView
    private lateinit var bpmText: TextView
    private lateinit var btnPlay: Button
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_drum)

        engine = (application as SoundBoxApp).drumEngine

        padView = findViewById(R.id.drumPad)
        bpmText = findViewById(R.id.bpmText)
        btnPlay = findViewById(R.id.btnPlay)
        val seekBpm = findViewById<SeekBar>(R.id.seekBpm)
        val btnClear = findViewById<Button>(R.id.btnClear)

        padView.pattern = engine.pattern
        padView.onToggle = { v, s ->
            engine.toggleCell(v, s)
            padView.pattern = engine.pattern
        }
        padView.onPreview = { v -> engine.trigger(v) }

        engine.onStepChanged = { step ->
            handler.post {
                padView.currentStep = step
                padView.invalidate()
            }
        }

        seekBpm.max = 240 - 40
        seekBpm.progress = engine.bpm - 40
        bpmText.text = "BPM: ${engine.bpm}"
        seekBpm.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, progress: Int, fromUser: Boolean) {
                val bpm = progress + 40
                engine.bpm = bpm
                bpmText.text = "BPM: $bpm"
            }
            override fun onStartTrackingTouch(sb: SeekBar?) {}
            override fun onStopTrackingTouch(sb: SeekBar?) {}
        })

        // Dong bo UI khi xoay man hinh
        if (engine.isPlaying) btnPlay.text = "⏹ Stop"

        btnPlay.setOnClickListener {
            if (engine.isPlaying) {
                engine.stop()
                btnPlay.text = "▶️ Play"
            } else {
                engine.start()
                btnPlay.text = "⏹ Stop"
            }
        }

        btnClear.setOnClickListener {
            engine.clearPattern()
            padView.pattern = engine.pattern
            padView.invalidate()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.onStepChanged = null
        engine.stop()
    }
}
