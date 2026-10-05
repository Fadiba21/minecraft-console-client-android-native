package app.mccdroid.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.mccdroid.logic.GeminiClient

/** State chat sementara: tetap ada saat popup ditutup, hilang saat proses aplikasi selesai. */
data class AiMessage(val fromAi: Boolean, val text: String)

object AiSession {
    val messages = mutableStateListOf<AiMessage>()
    var prompt by mutableStateOf("")
    var busy by mutableStateOf(false)
    var pending by mutableStateOf<GeminiClient.FunctionCall?>(null)
    var pendingPreview by mutableStateOf("")
    var status by mutableStateOf<String?>(null)

    fun clear() {
        messages.clear()
        prompt = ""
        busy = false
        pending = null
        pendingPreview = ""
        status = null
    }
}
