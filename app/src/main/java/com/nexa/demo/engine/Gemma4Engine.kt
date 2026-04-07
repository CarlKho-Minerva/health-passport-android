// Health Passport — Gemma 4 Engine
//
// Wraps Google AI Edge LiteRT-LM (com.google.ai.edge.litertlm) to run
// Gemma 4 E2B on-device via GPU (or CPU fallback).
//
// Model: litert-community/gemma-4-E2B-it-litert-lm (~2.58 GB)
// HuggingFace: https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm
//
// Licensed under the Apache License, Version 2.0.

package com.nexa.demo.engine

import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.SamplerConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Singleton-style manager for a single Gemma 4 Engine instance.
 *
 * Usage:
 *   val engine = Gemma4Engine(modelPath, systemPrompt)
 *   engine.initialize()          // suspend — call from IO coroutine
 *   engine.sendMessageStream(prompt).collect { token -> ... }
 *   engine.close()               // release native resources
 */
class Gemma4Engine(
    private val modelPath: String,
    private val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    private val cacheDir: String? = null,
    /** Use GPU backend when available; falls back to CPU if GPU init fails. */
    private val preferGpu: Boolean = true,
) : AutoCloseable {

    private var engine: Engine? = null

    companion object {
        private const val TAG = "Gemma4Engine"

        /**
         * Model filename for Gemma 4 E2B stored on HuggingFace litert-community.
         * Resolved download URL:
         *   https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-litert-lm.litertlm
         */
        const val MODEL_FILENAME = "gemma-4-E2B-it-litert-lm.litertlm"
        const val DOWNLOAD_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it-litert-lm.litertlm"
        const val MODEL_ID = "gemma4-e2b"
        const val SIZE_GB = 2.58

        private const val DEFAULT_SYSTEM_PROMPT =
            "You are a trusted medical record assistant for Health Passport. " +
            "You help users understand, retrieve, and summarize their personal health records. " +
            "Answer only from the provided health vault context. " +
            "If information is unavailable in the user's records, say so explicitly—never guess or invent medical facts. " +
            "Be concise, warm, and clinically accurate."
    }

    /**
     * Load the model file and initialise the LiteRT-LM engine.
     * Attempts GPU first; if that fails, retries with CPU.
     * Must be called on a background thread (IO dispatcher recommended).
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        check(engine == null) { "Gemma4Engine is already initialised. Call close() before re-initialising." }
        Log.i(TAG, "Initialising Gemma 4 E2B from: $modelPath")

        val config = buildEngineConfig(gpu = preferGpu)
        engine = try {
            Engine(config).also { it.initialize() }
        } catch (e: Exception) {
            if (preferGpu) {
                Log.w(TAG, "GPU init failed (${e.message}); retrying with CPU backend.")
                val cpuConfig = buildEngineConfig(gpu = false)
                Engine(cpuConfig).also { it.initialize() }
            } else {
                throw e
            }
        }
        Log.i(TAG, "Gemma 4 E2B initialised successfully.")
    }

    /**
     * Stream a response to [userMessage] as a Kotlin Flow of token strings.
     * Each emitted value is a partial response chunk; collect until the Flow completes.
     *
     * @param userMessage   The user's query text.
     * @param contextSnippet Optional pre-retrieved context from the health vault
     *                       (injected into the prompt before the user message).
     */
    fun sendMessageStream(
        userMessage: String,
        contextSnippet: String? = null,
    ): Flow<String> = flow {
        val eng = checkNotNull(engine) {
            "Engine not initialised. Call initialize() before sendMessageStream()."
        }

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemContent(contextSnippet)),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
        )

        eng.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessageAsync(userMessage)
                .catch { e -> Log.e(TAG, "Stream error: ${e.message}", e); throw e }
                .collect { message ->
                    val chunk = message.toString()
                    if (chunk.isNotEmpty()) emit(chunk)
                }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Single-shot (non-streaming) inference.
     * Prefer [sendMessageStream] for UI responsiveness.
     */
    suspend fun sendMessage(
        userMessage: String,
        contextSnippet: String? = null,
    ): String = withContext(Dispatchers.IO) {
        val eng = checkNotNull(engine) { "Engine not initialised." }

        val conversationConfig = ConversationConfig(
            systemInstruction = Contents.of(buildSystemContent(contextSnippet)),
            samplerConfig = SamplerConfig(topK = 40, topP = 0.95, temperature = 0.7),
        )

        eng.createConversation(conversationConfig).use { conversation ->
            conversation.sendMessage(userMessage).toString()
        }
    }

    /** Release engine resources. Safe to call multiple times. */
    override fun close() {
        try {
            engine?.close()
        } catch (e: Exception) {
            Log.w(TAG, "Exception while closing engine: ${e.message}")
        } finally {
            engine = null
        }
    }

    val isInitialised: Boolean get() = engine != null

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun buildEngineConfig(gpu: Boolean): EngineConfig {
        val backend = if (gpu) Backend.GPU() else Backend.CPU()
        return EngineConfig(
            modelPath = modelPath,
            backend = backend,
            cacheDir = cacheDir,
        )
    }

    /**
     * Build a system content string that optionally injects RAG context before
     * the base system prompt.
     */
    private fun buildSystemContent(contextSnippet: String?): String {
        return if (contextSnippet.isNullOrBlank()) {
            systemPrompt
        } else {
            """
            $systemPrompt

            --- Health Vault Context (use only this for answering) ---
            $contextSnippet
            --- End of Health Vault Context ---
            """.trimIndent()
        }
    }
}
