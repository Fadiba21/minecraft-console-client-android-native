package app.mccdroid.logic

import app.mccdroid.core.GeminiModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

/** Client REST Gemini API tanpa menyimpan API key di log atau URL. */
object GeminiClient {
    private const val BASE = "https://generativelanguage.googleapis.com/v1beta"

    data class FunctionCall(val name: String, val args: Map<String, Any?>)
    data class Reply(val text: String, val calls: List<FunctionCall>)

    suspend fun listModels(apiKey: String): List<GeminiModel> = withContext(Dispatchers.IO) {
        val root = request("$BASE/models?pageSize=1000", apiKey, "GET", null).asObj()
        root["models"].asArr().mapNotNull { raw ->
            val o = raw.asObj()
            val methods = o["supportedGenerationMethods"].asArr().mapNotNull { it as? String }
            val id = o.str("baseModelId").ifBlank { o.str("name").removePrefix("models/") }
            if (id.isBlank() || !methods.contains("generateContent")) null
            else GeminiModel(id, o.str("displayName", id), o.str("description"))
        }
    }

    suspend fun generate(apiKey: String, model: String, prompt: String, system: String, tools: List<Map<String, Any?>>): Reply = withContext(Dispatchers.IO) {
        val body = linkedMapOf<String, Any?>(
            "systemInstruction" to mapOf("parts" to listOf(mapOf("text" to system))),
            "contents" to listOf(mapOf("role" to "user", "parts" to listOf(mapOf("text" to prompt)))),
            "generationConfig" to mapOf("temperature" to 0.2, "maxOutputTokens" to 4096),
        )
        if (tools.isNotEmpty()) body["tools"] = listOf(mapOf("functionDeclarations" to tools))
        parseReply(request("$BASE/models/${model.removePrefix("models/")}:generateContent", apiKey, "POST", Json.stringify(body)).asObj())
    }

    private fun parseReply(root: Map<String, Any?>): Reply {
        val parts = root["candidates"].asArr().firstOrNull().asObj()["content"].asObj()["parts"].asArr()
        val text = parts.mapNotNull { it.asObj()["text"] as? String }.joinToString("\n")
        val calls = parts.mapNotNull { part ->
            val o = part.asObj()["functionCall"].asObj()
            val name = o.str("name")
            if (name.isBlank()) null else FunctionCall(name, o["args"].asObj())
        }
        return Reply(text, calls)
    }

    private fun request(url: String, apiKey: String, method: String, body: String?): Any? {
        require(apiKey.isNotBlank()) { "API key Gemini belum diatur." }
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("x-goog-api-key", apiKey)
            setRequestProperty("Content-Type", "application/json")
            doInput = true
            if (method == "POST") doOutput = true
        }
        try {
            if (body != null) c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val text = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            if (code !in 200..299) error("Gemini HTTP $code: ${text.take(500)}")
            return Json.parse(text)
        } finally {
            c.disconnect()
        }
    }
}
