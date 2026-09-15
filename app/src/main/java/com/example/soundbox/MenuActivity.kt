package com.example.soundbox

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView

class MenuActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)

        findViewById<MaterialCardView>(R.id.cardPiano).setOnClickListener {
            startActivity(Intent(this, PianoActivity::class.java))
        }
        findViewById<MaterialCardView>(R.id.cardDrum).setOnClickListener {
            startActivity(Intent(this, DrumActivity::class.java))
        }
    }
}
