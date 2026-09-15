package com.example.soundbox

import android.app.Application

class SoundBoxApp : Application() {
    lateinit var audioEngine: AudioEngine
        private set
    lateinit var drumEngine: DrumEngine
        private set

    override fun onCreate() {
        super.onCreate()
        audioEngine = AudioEngine()
        drumEngine = DrumEngine()
    }
}
