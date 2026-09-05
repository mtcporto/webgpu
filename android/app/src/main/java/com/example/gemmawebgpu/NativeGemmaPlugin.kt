package com.example.gemmawebgpu

import android.content.Context
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

@CapacitorPlugin(name = "NativeGemma")
class NativeGemmaPlugin : Plugin() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var engine: Engine? = null
    private var conversation: Conversation? = null

    @PluginMethod
    fun initialize(call: PluginCall) {
        val modelUrl = call.getString("modelUrl")
        val modelId = call.getString("modelId") ?: "gemma-model"
        if (modelUrl.isNullOrBlank()) {
            call.reject("modelUrl é obrigatório")
            return
        }

        scope.launch {
            try {
                closeEngine()
                val modelFile = downloadModel(modelUrl, modelId)
                val config = EngineConfig(
                    modelPath = modelFile.absolutePath,
                    backend = Backend.GPU(),
                    cacheDir = requireContext().cacheDir.absolutePath,
                )
                val newEngine = Engine(config)
                newEngine.initialize()
                val newConversation = newEngine.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "Você é um assistente prestativo e inteligente. " +
                                "Responda sempre em português quando o usuário escrever em português. " +
                                "Seja direto, claro e objetivo."
                        )
                    )
                )
                engine = newEngine
                conversation = newConversation
                call.resolve(JSObject().put("backend", "GPU").put("modelPath", modelFile.absolutePath))
            } catch (error: Throwable) {
                closeEngine()
                reject(call, error)
            }
        }
    }

    @PluginMethod
    @OptIn(ExperimentalApi::class)
    fun sendMessage(call: PluginCall) {
        val text = call.getString("text")
        if (text.isNullOrBlank()) {
            call.reject("text é obrigatório")
            return
        }

        scope.launch {
            try {
                val activeConversation = conversation ?: throw IllegalStateException("O modelo ainda não foi inicializado")
                val response = activeConversation.sendMessage(text)
                val rendered = activeConversation.renderMessageIntoString(response)
                call.resolve(JSObject().put("text", rendered))
            } catch (error: Throwable) {
                reject(call, error)
            }
        }
    }

    @PluginMethod
    fun shutdown(call: PluginCall) {
        scope.launch {
            closeEngine()
            call.resolve()
        }
    }

    private fun downloadModel(modelUrl: String, modelId: String): File {
        val modelsDir = File(requireContext().filesDir, "models").apply { mkdirs() }
        val safeId = modelId.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val target = File(modelsDir, "$safeId.litertlm")
        if (target.exists() && target.length() > 0L) return target

        val partial = File(modelsDir, "$safeId.litertlm.part")
        val connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 30_000
            readTimeout = 120_000
            instanceFollowRedirects = true
            requestMethod = "GET"
        }
        connection.connect()
        if (connection.responseCode !in 200..299) {
            throw IllegalStateException("Download do modelo falhou: HTTP ${connection.responseCode}")
        }
        connection.inputStream.use { input ->
            partial.outputStream().use { output -> input.copyTo(output, DEFAULT_BUFFER_SIZE) }
        }
        if (!partial.renameTo(target)) throw IllegalStateException("Não foi possível guardar o modelo")
        return target
    }

    private fun requireContext(): Context = activity.applicationContext

    private fun reject(call: PluginCall, error: Throwable) {
        val exception = error as? Exception ?: RuntimeException(error.message, error)
        call.reject(error.message ?: error.javaClass.simpleName, exception)
    }

    private fun closeEngine() {
        conversation?.close()
        conversation = null
        engine?.close()
        engine = null
    }

    override fun handleOnDestroy() {
        closeEngine()
        scope.coroutineContext.cancel()
        super.handleOnDestroy()
    }
}
