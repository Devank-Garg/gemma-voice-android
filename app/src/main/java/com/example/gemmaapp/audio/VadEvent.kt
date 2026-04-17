package com.example.gemmaapp.audio

sealed class VadEvent {
    object SpeechStart : VadEvent()
    data class SpeechEnd(val pcm: FloatArray) : VadEvent()
    object Timeout : VadEvent()
}
