package com.tradelikeahedgefund.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.tlhf.shared.ai.LlmEngine

/**
 * Android on-device LLM via MediaPipe `LlmInference` (tasks-genai).
 * Reads the `.task` model file downloaded by [ModelDownloader].
 *
 * Same settings as the web app's Capacitor plugin: maxTokens 2048,
 * async streaming generation, one generation at a time.
 */
class MediaPipeEngine(private val context: Context) : LlmEngine {

    override val isAvailable: Boolean = true

    private var llm: LlmInference? = null
    private val genLock = Any()
    private var generating = false

    override fun loadModel(modelPath: String, onDone: (error: String?) -> Unit) {
        Thread {
            try {
                unload()
                val options = LlmInference.LlmInferenceOptions.builder()
                    .setModelPath(modelPath)
                    .setMaxTokens(2048)
                    .build()
                llm = LlmInference.createFromOptions(context, options)
                onDone(null)
            } catch (e: Exception) {
                onDone("could not load model: ${e.message}")
            }
        }.start()
    }

    override fun generate(
        prompt: String,
        onToken: (partialText: String) -> Unit,
        onDone: (fullText: String?, error: String?) -> Unit
    ) {
        val engine = llm
        if (engine == null) {
            onDone(null, "model not loaded")
            return
        }
        synchronized(genLock) {
            if (generating) {
                onDone(null, "already_generating")
                return
            }
            generating = true
        }
        Thread {
            try {
                // MediaPipe streams CUMULATIVE text (each callback has the full
                // response so far) — forward it as-is, like the web app did.
                engine.generateResponseAsync(prompt, ProgressListener<String> { partial, done ->
                    onToken(partial)
                    if (done) {
                        synchronized(genLock) { generating = false }
                        onDone(partial, null)
                    }
                })
            } catch (e: Exception) {
                synchronized(genLock) { generating = false }
                onDone(null, e.message ?: "generation failed")
            }
        }.start()
    }

    override fun unload() {
        try { llm?.close() } catch (_: Exception) { }
        llm = null
    }
}
