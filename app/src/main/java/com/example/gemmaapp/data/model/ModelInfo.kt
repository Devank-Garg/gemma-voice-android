package com.example.gemmaapp.data.model

data class ModelInfo(
    val name: String,
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,       // empty = skip verification; fill from HuggingFace model card
    val huggingFaceRepo: String,
    val downloadUrl: String
)

val GEMMA_4_E2B = ModelInfo(
    name = "Gemma 4 E2B",
    fileName = "gemma-4-E2B-it.litertlm",
    sizeBytes = 2_580_000_000L,
    sha256 = "",
    huggingFaceRepo = "litert-community/gemma-4-e2b-it",
    // confirm exact filename from https://huggingface.co/litert-community/gemma-4-e2b-it/tree/main
    downloadUrl = "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
)
