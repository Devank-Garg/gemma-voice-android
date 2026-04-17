package com.example.gemmaapp.inference

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LiteRtLmEngine @Inject constructor() {
    // Sprint 3: load .litertlm model, GPU delegate with CPU fallback
    // init takes 5-10s — called once on app start, kept alive for session
    fun load(modelPath: String) { TODO("Sprint 3") }
    fun close() { TODO("Sprint 3") }
}
