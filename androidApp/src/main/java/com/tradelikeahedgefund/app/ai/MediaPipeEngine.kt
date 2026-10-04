package com.tradelikeahedgefund.app.ai

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.google.mediapipe.tasks.genai.llminference.ProgressListener
import com.tlhf.shared.ai.LlmEngine
import com.tlhf.shared.ai.wrapChatMlUserTurn

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
                // Qwen needs ChatML framing: the shared builders return plain
                // text, and sending it raw (no <|im_start|> turn structure)
                // made the model return empty completions. iOS already wraps
                // its prompts in LlamaEngine.swift.
                val framed = wrapChatMlUserTurn(prompt)
                // Do NOT trust any single callback's `partial` as the full
                // text: in tasks-genai the terminal callback can carry an
                // empty / last-chunk string instead of the whole response,
                // which is exactly how empty answer bubbles were saved.
                // Accumulate ourselves — a partial that starts with what we
                // already have is a cumulative snapshot, anything else is a
                // delta chunk to append.
                var accumulated = ""
                engine.generateResponseAsync(framed, ProgressListener<String> { partial, done ->
                    val chunk = partial ?: ""
                    accumulated = if (chunk.startsWith(accumulated)) chunk else accumulated + chunk
                    onToken(accumulated)
                    if (done) {
                        synchronized(genLock) { generating = false }
                        if (accumulated.isBlank()) {
                            onDone(null, "The model didn't produce a reply — please try again.")
                        } else {
                            onDone(accumulated, null)
                        }
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
