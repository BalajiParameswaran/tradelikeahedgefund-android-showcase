package com.tlhf.shared.ai

import kotlinx.serialization.Serializable

/**
 * Bundled on-device model catalog — ported from the web app's models.json.
 *
 * The catalog lives IN the app (never fetched remotely): no external catalog
 * read, no tracking, no extra host to maintain.
 *
 * Android runs the LiteRT/MediaPipe `.task` builds; iOS runs the GGUF builds
 * through llama.cpp. Same model family (Qwen2.5-Instruct, q8) on both.
 */
@Serializable
data class AiModel(
    val id: String,
    val name: String,
    val model: String,
    val quant: String,
    val desc: String,
    val license: String,
    val sizeBytes: Long,
    val sizeLabel: String,
    /** LiteRT/MediaPipe build for Android. */
    val androidUrl: String,
    val androidFileName: String,
    /** GGUF build for iOS (llama.cpp). */
    val iosUrl: String,
    val iosFileName: String,
    val isDefault: Boolean = false
)

val AI_MODELS: List<AiModel> = listOf(
    AiModel(
        id = "faster",
        name = "Faster",
        model = "Qwen2.5-0.5B-Instruct",
        quant = "q8",
        desc = "Quick answers, weaker reasoning.",
        license = "Apache 2.0",
        sizeBytes = 546_660_344L,
        sizeLabel = "~0.52 GB",
        androidUrl = "https://huggingface.co/litert-community/Qwen2.5-0.5B-Instruct/resolve/main/Qwen2.5-0.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        androidFileName = "qwen2.5-0.5b-instruct.task",
        iosUrl = "https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q8_0.gguf?download=true",
        iosFileName = "qwen2.5-0.5b-instruct-q8_0.gguf"
    ),
    AiModel(
        id = "balanced",
        name = "Balanced",
        model = "Qwen2.5-1.5B-Instruct",
        quant = "q8",
        desc = "Best mix of smarts and speed.",
        license = "Apache 2.0",
        sizeBytes = 1_597_913_616L,
        sizeLabel = "~1.49 GB",
        androidUrl = "https://huggingface.co/litert-community/Qwen2.5-1.5B-Instruct/resolve/main/Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv1280.task?download=true",
        androidFileName = "qwen2.5-1.5b-instruct.task",
        iosUrl = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q8_0.gguf?download=true",
        iosFileName = "qwen2.5-1.5b-instruct-q8_0.gguf",
        isDefault = true
    )
)

fun defaultAiModel(): AiModel = AI_MODELS.first { it.isDefault }

fun aiModelById(id: String): AiModel = AI_MODELS.firstOrNull { it.id == id } ?: defaultAiModel()
