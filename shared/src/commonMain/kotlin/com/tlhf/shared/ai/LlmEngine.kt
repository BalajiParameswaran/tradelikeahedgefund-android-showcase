package com.tlhf.shared.ai

/**
 * Platform on-device LLM. 100% on-device: prompts and responses never leave
 * the phone.
 *
 * - Android: MediaPipe `LlmInference` reading the `.task` model file
 *   (see androidApp/.../ai/MediaPipeEngine.kt).
 * - iOS: llama.cpp reading the GGUF model file
 *   (see iosApp/TradeLikeAHedgeFund/LlamaEngine.swift).
 *
 * Callback style (no coroutines in the signature) so both the MediaPipe
 * listener API and Swift closures map onto it directly.
 */
interface LlmEngine {

    /** False when the device can't run on-device AI (checked at startup). */
    val isAvailable: Boolean

    /**
     * Load the model file at [modelPath]. [onDone] receives null on success,
     * or a human-readable error message on failure.
     */
    fun loadModel(modelPath: String, onDone: (error: String?) -> Unit)

    /**
     * Generate one completion for [prompt]. Streams cumulative partial text to
     * [onToken]; [onDone] receives the full text or an error. Only one
     * generation runs at a time — a second call fails fast with
     * "already_generating" so the UI can tell the user to wait.
     */
    fun generate(
        prompt: String,
        onToken: (partialText: String) -> Unit,
        onDone: (fullText: String?, error: String?) -> Unit
    )

    /** Release the model from RAM (e.g. on 30-minute idle). */
    fun unload()
}
