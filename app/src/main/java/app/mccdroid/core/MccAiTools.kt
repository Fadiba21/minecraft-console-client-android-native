package app.mccdroid.core

import android.content.Context
import app.mccdroid.logic.ActionType
import app.mccdroid.logic.Automation
import app.mccdroid.logic.MatchType
import app.mccdroid.logic.NotifyLogic
import app.mccdroid.logic.Json
import app.mccdroid.logic.Rule
import app.mccdroid.logic.RuleAction
import app.mccdroid.logic.TriggerKind
import java.io.File
import java.util.UUID

/** Tool yang boleh dipanggil Gemini. Tidak ada akses shell atau path di luar sandbox aplikasi. */
object MccAiTools {
    val declarations: List<Map<String, Any?>> = listOf(
        tool("list_profiles", "Daftar profil MCC yang tersedia", emptyMap(), emptyList()),
        tool("read_app_state", "Membaca status lengkap profil, otomasi, notifikasi, dan setting aplikasi MCC Droid", emptyMap(), emptyList()),
        tool("read_all_data", "Membaca seluruh file teks data MCC Droid yang aman dibaca", emptyMap(), emptyList()),
        tool("read_profile_data", "Membaca config, log, dan file teks satu profil MCC", mapOf("profileId" to prop("string")), listOf("profileId")),
        tool("read_runtime_info", "Membaca daftar file dan metadata runtime MCC tanpa mengubah binary", emptyMap(), emptyList()),
        tool("read_config", "Membaca MinecraftClient.ini sebuah profil", mapOf("profileId" to prop("string")), listOf("profileId")),
        tool("write_config", "Menulis penuh MinecraftClient.ini sebuah profil", mapOf("profileId" to prop("string"), "content" to prop("string")), listOf("profileId", "content")),
        tool("list_files", "Daftar file di folder data MCC", emptyMap(), emptyList()),
        tool("read_file", "Membaca file relatif di folder data MCC", mapOf("path" to prop("string")), listOf("path")),
        tool("write_file", "Membuat atau menimpa file relatif di folder data MCC", mapOf("path" to prop("string"), "content" to prop("string")), listOf("path", "content")),
        tool("create_script", "Membuat script C# MCC di shared/scripts", mapOf("name" to prop("string"), "content" to prop("string")), listOf("name", "content")),
        tool("create_automation", "Membuat otomasi pemicu chat/log dengan aksi command, notifikasi, delay, stop, atau restart", mapOf(
            "name" to prop("string"), "pattern" to prop("string"), "command" to prop("string"), "profileId" to prop("string"),
        ), listOf("name", "pattern", "command")),
        tool("set_app_setting", "Mengubah setting aplikasi yang diizinkan", mapOf("key" to prop("string"), "value" to prop("string")), listOf("key", "value")),
        tool("send_mcc_command", "Mengirim command slash ke sesi MCC", mapOf("profileId" to prop("string"), "command" to prop("string")), listOf("profileId", "command")),
    )

    val confirmationRequired = setOf("write_config", "write_file", "create_script", "create_automation", "set_app_setting", "send_mcc_command")

    fun preview(name: String, args: Map<String, Any?>): String = when (name) {
        "write_config" -> "Tulis ulang config profil ${args.str("profileId")}"
        "write_file" -> "Tulis file ${args.str("path")}"
        "create_script" -> "Buat script ${args.str("name")}"
        "create_automation" -> "Buat otomasi ${args.str("name")} untuk pola ${args.str("pattern")}"
        "set_app_setting" -> "Ubah setting aplikasi ${args.str("key")}"
        "send_mcc_command" -> "Kirim ${args.str("command")} ke profil ${args.str("profileId")}"
        else -> name
    }

    fun execute(ctx: Context, name: String, args: Map<String, Any?>): String {
        return when (name) {
            "list_profiles" -> ProfileStore.profiles.value.joinToString("\n") { "${it.id}: ${it.name}" }.ifBlank { "Belum ada profil." }
            "read_app_state" -> readAppState(ctx)
            "read_all_data" -> readAllData(ctx)
            "read_profile_data" -> readProfileData(ctx, args.str("profileId"))
            "read_runtime_info" -> listFiles(Paths.runtimeDir(ctx))
            "read_config" -> readConfig(ctx, args.str("profileId"))
            "list_files" -> listFiles(Paths.dataRoot(ctx))
            "read_file" -> safeFile(ctx, args.str("path")).readText(Charsets.UTF_8).take(MAX_READ)
            "write_config" -> {
                val id = args.str("profileId")
                require(ProfileStore.get(id) != null) { "Profil tidak ditemukan." }
                val file = Paths.configFile(ctx, id)
                if (file.exists()) file.copyTo(File(file.parentFile, file.name + ".bak"), overwrite = true)
                Paths.writeAtomic(file, args.str("content"))
                "Config profil $id tersimpan. Cadangan .bak dibuat bila sebelumnya ada."
            }
            "write_file" -> writeFile(ctx, args.str("path"), args.str("content"))
            "create_script" -> {
                val name0 = args.str("name").substringAfterLast('/').substringAfterLast('\\').replace(Regex("[^A-Za-z0-9_.-]"), "_").let { if (it.endsWith(".cs")) it else "$it.cs" }
                val file = File(Paths.scriptsDir(ctx), name0)
                Paths.writeAtomic(file, args.str("content"))
                "Script ${file.name} dibuat di shared/scripts."
            }
            "create_automation" -> {
                val command = args.str("command")
                require(command.startsWith("/")) { "Command otomasi harus diawali '/'." }
                val rule = Rule(
                    id = UUID.randomUUID().toString(), name = args.str("name").ifBlank { "Otomasi AI" },
                    profileId = args.str("profileId"), trigger = TriggerKind.LINE, match = MatchType.CONTAINS,
                    pattern = args.str("pattern"), actions = listOf(RuleAction(ActionType.SEND, command)),
                )
                RuleStore.upsert(rule)
                "Otomasi ${rule.name} dibuat dan aktif."
            }
            "set_app_setting" -> setAppSetting(args.str("key"), args.str("value"))
            "send_mcc_command" -> {
                val command = args.str("command")
                require(command.startsWith("/")) { "Hanya command slash yang boleh dikirim." }
                val id = args.str("profileId")
                require(ProfileStore.get(id) != null) { "Profil tidak ditemukan." }
                SessionManager.session(id).send(command)
                "Command dikirim ke profil $id."
            }
            else -> error("Tool AI tidak dikenal: $name")
        }
    }

    private fun readConfig(ctx: Context, id: String): String {
        require(ProfileStore.get(id) != null) { "Profil tidak ditemukan." }
        val f = Paths.configFile(ctx, id)
        return if (f.exists()) f.readText(Charsets.UTF_8).take(MAX_READ) else "Config belum ada."
    }

    private fun readAppState(ctx: Context): String = buildString {
        appendLine("[PROFILES]")
        appendLine(ProfileStore.profiles.value.joinToString("\n") { Json.stringify(mapOf("id" to it.id, "name" to it.name, "autoStart" to it.autoStart, "autoRestart" to it.autoRestart, "extraArgs" to it.extraArgs)) })
        appendLine("[AUTOMATIONS]")
        appendLine(Automation.toJson(RuleStore.rules.value).take(MAX_READ))
        appendLine("[NOTIFICATIONS]")
        appendLine(NotifyLogic.toJson(NotifyStore.config.value).take(MAX_READ))
        appendLine("[APP_SETTINGS]")
        appendLine("themeMode=${AppPrefs.themeMode}, consoleFontSp=${AppPrefs.consoleFontSp}, maxLogLines=${AppPrefs.maxLogLines}, wrapLines=${AppPrefs.wrapLines}, showTimestamps=${AppPrefs.showTimestamps}")
        appendLine("[DATA_ROOT]")
        appendLine(Paths.dataRoot(ctx).absolutePath)
    }.take(MAX_READ)

    private fun readAllData(ctx: Context): String = buildString {
        val root = Paths.dataRoot(ctx)
        root.walkTopDown().filter { it.isFile && it.length() <= MAX_READ && isTextFile(it) }.take(200).forEach { f ->
            appendLine("--- ${f.relativeTo(root).path} ---")
            appendLine(runCatching { f.readText(Charsets.UTF_8).take(16 * 1024) }.getOrDefault("<tidak dapat dibaca>"))
        }
    }.take(MAX_READ)

    private fun readProfileData(ctx: Context, id: String): String {
        require(ProfileStore.get(id) != null) { "Profil tidak ditemukan." }
        val root = Paths.profileDir(ctx, id)
        return buildString {
            root.walkTopDown().filter { it.isFile && it.length() <= MAX_READ && isTextFile(it) }.take(100).forEach { f ->
                appendLine("--- ${f.relativeTo(root).path} ---")
                appendLine(runCatching { f.readText(Charsets.UTF_8).take(16 * 1024) }.getOrDefault("<tidak dapat dibaca>"))
            }
        }.take(MAX_READ)
    }

    private fun isTextFile(file: File): Boolean = file.extension.lowercase() in setOf(
        "ini", "json", "txt", "log", "cs", "toml", "yaml", "yml", "conf", "cfg", "properties", "md",
    )

    private fun writeFile(ctx: Context, path: String, content: String): String {
        val f = safeFile(ctx, path)
        require(content.length <= MAX_WRITE) { "File terlalu besar." }
        Paths.writeAtomic(f, content)
        return "File ${f.relativeTo(Paths.dataRoot(ctx)).path} tersimpan."
    }

    private fun safeFile(ctx: Context, relative: String): File {
        val root = Paths.dataRoot(ctx).canonicalFile
        val f = File(root, relative.trimStart('/')).canonicalFile
        require(f.path == root.path || f.path.startsWith(root.path + File.separator)) { "Path di luar folder data MCC ditolak." }
        return f
    }

    private fun listFiles(root: File): String = root.walkTopDown().filter { it.isFile }.take(200)
        .joinToString("\n") { it.relativeTo(root).path }
        .ifBlank { "Belum ada file." }

    private fun setAppSetting(key: String, value: String): String {
        when (key) {
            "themeMode" -> require(value in setOf("system", "dark", "light", "amoled")) { "Tema tidak valid." }.also { AppPrefs.themeMode = value }
            "consoleFontSp" -> AppPrefs.consoleFontSp = value.toFloatOrNull()?.coerceIn(9f, 20f) ?: error("Ukuran font tidak valid.")
            "maxLogLines" -> AppPrefs.maxLogLines = value.toIntOrNull()?.coerceIn(500, 10000) ?: error("Batas log tidak valid.")
            "wrapLines" -> AppPrefs.wrapLines = value.toBooleanStrictOrNull() ?: error("Nilai boolean tidak valid.")
            "showTimestamps" -> AppPrefs.showTimestamps = value.toBooleanStrictOrNull() ?: error("Nilai boolean tidak valid.")
            "saveConsoleLog" -> AppPrefs.saveConsoleLog = value.toBooleanStrictOrNull() ?: error("Nilai boolean tidak valid.")
            "keepScreenOn" -> AppPrefs.keepScreenOn = value.toBooleanStrictOrNull() ?: error("Nilai boolean tidak valid.")
            else -> error("Setting aplikasi tidak diizinkan: $key")
        }
        return "Setting $key diubah."
    }

    private fun prop(type: String) = mapOf("type" to type)
    private fun tool(name: String, description: String, properties: Map<String, Any?>, required: List<String>) = mapOf(
        "name" to name, "description" to description,
        "parameters" to mapOf("type" to "object", "properties" to properties, "required" to required),
    )
    private const val MAX_READ = 64 * 1024
    private const val MAX_WRITE = 256 * 1024
}

private fun Map<String, Any?>.str(key: String): String = (this[key] as? String).orEmpty()
