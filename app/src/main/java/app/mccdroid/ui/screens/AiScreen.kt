package app.mccdroid.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import app.mccdroid.core.AiMessage
import app.mccdroid.core.AiSession
import app.mccdroid.core.GeminiStore
import app.mccdroid.core.MccAiTools
import app.mccdroid.logic.GeminiClient
import app.mccdroid.ui.Nav
import app.mccdroid.ui.components.GlassCard
import app.mccdroid.ui.components.ScreenHeader
import app.mccdroid.core.UiSound
import kotlinx.coroutines.launch

@Composable
fun AiScreen(nav: Nav, onClose: (() -> Unit)? = null) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    fun ask() {
        val text = AiSession.prompt.trim()
        if (text.isEmpty() || AiSession.busy) return
        if (GeminiStore.apiKey.isBlank()) {
            AiSession.status = "Atur API key Gemini di Lainnya → Pengaturan terlebih dahulu."
            UiSound.alert()
            return
        }
        AiSession.messages += AiMessage(false, text)
        AiSession.prompt = ""
        AiSession.busy = true
        AiSession.status = null
        val conversation = AiSession.messages.dropLast(1).map { it.fromAi to it.text }
        UiSound.send()
        UiSound.processing()
        scope.launch {
            try {
                val reply = GeminiClient.generate(
                    GeminiStore.apiKey,
                    GeminiStore.selectedModel,
                    text,
                    """Anda adalah MCC Droid AI Assistant. Pahami percakapan sebelumnya dan tujuan pengguna sebelum menjawab.
Anda dapat membaca serta mengubah seluruh data MCC Droid yang berada di sandbox aplikasi: profil, MinecraftClient.ini, log, file txt/json/md, script C#, otomasi, notifikasi, dan setting aplikasi yang tersedia dalam tool.
Saat konteks belum cukup, gunakan read_app_state, read_profile_data, read_all_data, read_config, atau read_file sebelum menyimpulkan.
Saat pengguna meminta perubahan, rencanakan langkahnya, gunakan tool yang paling tepat, tampilkan ringkasan hasil kepada pengguna, dan tunggu konfirmasi untuk setiap operasi tulis/kirim.
Jangan menghapus data permanen, menjalankan shell Android bebas, menulis binary runtime, mengakses path di luar sandbox MCC, atau membocorkan API key. Selalu jawab dalam bahasa pengguna dan jelaskan file/setting yang berubah.""",
                    MccAiTools.declarations,
                    conversation,
                )
                if (reply.text.isNotBlank()) AiSession.messages += AiMessage(true, reply.text)
                val call = reply.calls.firstOrNull()
                if (call != null) {
                    AiSession.pending = call
                    AiSession.pendingPreview = MccAiTools.preview(call.name, call.args)
                }
                UiSound.success()
            } catch (e: Exception) {
                AiSession.status = e.message ?: "Permintaan Gemini gagal."
                UiSound.alert()
            } finally {
                AiSession.busy = false
            }
        }
    }

    val close = onClose ?: { nav.sub = null }
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(Modifier.fillMaxSize()) {
            ScreenHeader(
                "Gemini Assistant",
                "${GeminiStore.selectedModel} · data MCC sandbox",
                actions = {
                    IconButton(onClick = { UiSound.close(); close() }) {
                        Icon(if (onClose == null) Icons.Rounded.ArrowBack else Icons.Rounded.Close, "Tutup Gemini")
                    }
                },
            )
            if (AiSession.busy) {
                LinearProgressIndicator(Modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.primary)
            }
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (AiSession.messages.isEmpty()) {
                    item {
                        GlassCard(accent = MaterialTheme.colorScheme.primary) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                Text("Gemini dapat membaca dan mengedit data internal MCC Droid: profil, config, log, script, file, otomasi, dan setting aplikasi.")
                            }
                        }
                    }
                }
                items(AiSession.messages) { message -> ChatBubble(message) }
                if (AiSession.busy) item { ThinkingBubble() }
                AiSession.pending?.let { call ->
                    item {
                        GlassCard(accent = MaterialTheme.colorScheme.tertiary) {
                            Text("Gemini meminta tindakan", style = MaterialTheme.typography.titleSmall)
                            Text(AiSession.pendingPreview, modifier = Modifier.padding(top = 4.dp))
                            Text("Belum dijalankan. Periksa lalu setujui atau tolak.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = {
                                    try {
                                        val result = MccAiTools.execute(ctx, call.name, call.args)
                                        AiSession.messages += AiMessage(true, result)
                                        AiSession.pending = null
                                        UiSound.success()
                                    } catch (e: Exception) {
                                        AiSession.status = e.message ?: "Tool gagal dijalankan."
                                        UiSound.alert()
                                    }
                                }) { Icon(Icons.Rounded.Check, null); Text(" Terapkan") }
                                OutlinedButton(onClick = { AiSession.pending = null; UiSound.click() }) { Text("Tolak") }
                            }
                        }
                    }
                }
            }
            AiSession.status?.let { Text(it, Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = AiSession.prompt,
                    onValueChange = { AiSession.prompt = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(if (AiSession.busy) "Gemini sedang bekerja…" else "Tanya atau minta ubah sistem MCC…") },
                    enabled = !AiSession.busy,
                    shape = RoundedCornerShape(18.dp),
                    singleLine = false,
                    maxLines = 4,
                )
                IconButton(onClick = ::ask, enabled = !AiSession.busy && AiSession.prompt.isNotBlank()) {
                    Icon(Icons.Rounded.Send, "Kirim ke Gemini")
                }
            }
        }
    }
}

@Composable
private fun ChatBubble(message: AiMessage) {
    val isAi = message.fromAi
    val brush = if (isAi) Brush.linearGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = .22f), MaterialTheme.colorScheme.surface))
    else Brush.linearGradient(listOf(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.surface))
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.background(brush).padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isAi) Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(6.dp))
                Text(if (isAi) "Gemini" else "Anda", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            Text(message.text, modifier = Modifier.padding(top = 5.dp), fontFamily = if (isAi) FontFamily.Default else FontFamily.Monospace)
        }
    }
}

@Composable
private fun ThinkingBubble() {
    val transition = rememberInfiniteTransition(label = "thinking")
    val alpha by transition.animateFloat(0.35f, 1f, infiniteRepeatable(tween(650), RepeatMode.Reverse), label = "thinking-alpha")
    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha))
        Text("  Gemini sedang membaca sistem MCC…", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha))
    }
}
