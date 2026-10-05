package app.mccdroid.logic

/** Potongan teks berwarna hasil parsing kode warna Minecraft (§) / ANSI. */
data class Span(
    val text: String,
    val rgb: Int? = null,
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val strike: Boolean = false,
)

/**
 * MCC (mode BasicIO) menulis kode warna Minecraft mentah, mis. "§c" untuk merah, ke stdout.
 * Objek ini mengubahnya menjadi span berwarna, atau membuangnya (strip) untuk analisis.
 */
object McText {
    private const val SECTION = '§'
    private const val ESC = '\u001B'

    private val MC_COLORS = intArrayOf(
        0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
        0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF,
    )
    private val ANSI_STD = intArrayOf(0x000000, 0xAA0000, 0x00AA00, 0xAA5500, 0x0000AA, 0xAA00AA, 0x00AAAA, 0xAAAAAA)
    private val ANSI_BRIGHT = intArrayOf(0x555555, 0xFF5555, 0x55FF55, 0xFFFF55, 0x5555FF, 0xFF55FF, 0x55FFFF, 0xFFFFFF)

    private val URL_REGEX = Regex("https?://[^\\s<>\"')\\]]+")
    // Beberapa plugin mengirim warna sebagai &#RRGGBB / #RRGGBB, atau kehilangan
    // pemisah sehingga kode warna hex muncul sebagai token panjang di tengah chat.
    private val PREFIXED_HEX = Regex("(?i)(?:&|#)[0-9a-f]{6}")
    private val BARE_HEX_TOKEN = Regex("(?i)(?<![a-z0-9])(?:[0-9a-f]{12,}(?=[a-z\\s]|$)|[0-9a-f]{6}(?=[A-Z]))")

    /** Normalisasi escape Unicode, mojibake UTF-8, dan kontrol terminal sebelum dirender. */
    fun normalize(s: String): String {
        val unescaped = decodeUnicodeEscapes(s)
        val repaired = repairMojibake(unescaped)
        val noPrefixedColors = repaired.replace(PREFIXED_HEX, "")
        val bareCandidates = BARE_HEX_TOKEN.findAll(noPrefixedColors).toList()
        // Hanya aktifkan heuristik bare-hex bila ada beberapa kandidat atau replacement
        // character; nama/pesan normal yang kebetulan mengandung enam huruf hex tidak dihapus.
        val noBareNoise = if (bareCandidates.size >= 2 || noPrefixedColors.contains('\uFFFD')) {
            noPrefixedColors.replace(BARE_HEX_TOKEN, "")
        } else noPrefixedColors
        return noBareNoise.filter { it == '\t' || it == '\n' || it == '\r' || it == ESC || !it.isISOControl() }
    }

    private fun decodeUnicodeEscapes(s: String): String {
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            if (s[i] == '\\' && i + 5 < s.length && s[i + 1] == 'u') {
                val code = s.substring(i + 2, i + 6).toIntOrNull(16)
                if (code != null) {
                    sb.append(code.toChar())
                    i += 6
                    continue
                }
            }
            sb.append(s[i++])
        }
        return sb.toString()
    }

    private fun repairMojibake(s: String): String {
        if (!s.any { it == 'Ã' || it == 'Â' || it == 'â' || it == 'ð' }) return s
        return try {
            val candidate = String(s.toByteArray(Charsets.ISO_8859_1), Charsets.UTF_8)
            if (candidate != s && candidate.count { it == '\uFFFD' } <= s.count { it == '\uFFFD' }) candidate else s
        } catch (_: Exception) {
            s
        }
    }

    /** Hapus semua kode § dan urutan ANSI. */
    fun strip(s: String): String {
        if (s.indexOf(SECTION) < 0 && s.indexOf(ESC) < 0) return s
        val sb = StringBuilder(s.length)
        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == SECTION) {
                // Dukungan tambahan untuk format legacy non-standar: §RRGGBB.
                i += if (i + 6 < s.length && s.substring(i + 1, i + 7).isHexColor()) 7
                else if (i + 1 < s.length) 2 else 1
            } else if (c == ESC) {
                i = skipAnsi(s, i)
            } else {
                sb.append(c)
                i++
            }
        }
        return sb.toString()
    }

    /** Posisi setelah urutan ANSI yang dimulai di [start] (yang berisi ESC). */
    private fun skipAnsi(s: String, start: Int): Int {
        var i = start + 1
        if (i < s.length && s[i] == '[') {
            i++
            while (i < s.length && !(s[i] in '@'..'~')) i++
            return if (i < s.length) i + 1 else s.length
        }
        return minOf(i, s.length)
    }

    fun findUrls(clean: String): List<IntRange> =
        URL_REGEX.findAll(clean).map { it.range.first..(it.range.last) }.toList()

    fun parse(s: String): List<Span> {
        val spans = ArrayList<Span>()
        val buf = StringBuilder()
        var color: Int? = null
        var bold = false
        var italic = false
        var underline = false
        var strike = false

        fun flush() {
            if (buf.isNotEmpty()) {
                spans.add(Span(buf.toString(), color, bold, italic, underline, strike))
                buf.setLength(0)
            }
        }

        var i = 0
        while (i < s.length) {
            val c = s[i]
            if (c == SECTION && i + 1 < s.length) {
                if (i + 6 < s.length && s.substring(i + 1, i + 7).isHexColor()) {
                    flush()
                    color = s.substring(i + 1, i + 7).toInt(16)
                    bold = false; italic = false; underline = false; strike = false
                    i += 7
                    continue
                }
                // Minecraft extended hex color: §x§R§R§G§G§B§B.
                if (s[i + 1].lowercaseChar() == 'x' && i + 13 < s.length) {
                    val hex = buildString {
                        for (p in 2..12 step 2) {
                            if (s[i + p] != SECTION) break
                            append(s[i + p + 1])
                        }
                    }
                    if (hex.length == 6 && hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) {
                        flush()
                        color = hex.toInt(16)
                        bold = false; italic = false; underline = false; strike = false
                        i += 14
                        continue
                    }
                }
                val code = s[i + 1].lowercaseChar()
                i += 2
                val idx = "0123456789abcdef".indexOf(code)
                if (idx >= 0) {
                    flush()
                    color = MC_COLORS[idx]
                    bold = false; italic = false; underline = false; strike = false
                } else when (code) {
                    'l' -> { flush(); bold = true }
                    'o' -> { flush(); italic = true }
                    'n' -> { flush(); underline = true }
                    'm' -> { flush(); strike = true }
                    'r' -> {
                        flush()
                        color = null; bold = false; italic = false; underline = false; strike = false
                    }
                    else -> { /* 'k' (acak) dan kode tak dikenal diabaikan */ }
                }
            } else if (c == ESC) {
                val end = skipAnsi(s, i)
                if (i + 1 < s.length && s[i + 1] == '[' && end > i && s[end - 1] == 'm') {
                    flush()
                    val params = s.substring(i + 2, end - 1)
                    val st = applySgr(params, color, bold, italic, underline, strike)
                    color = st.rgb; bold = st.bold; italic = st.italic; underline = st.underline; strike = st.strike
                }
                i = end
            } else {
                buf.append(c)
                i++
            }
        }
        flush()
        return spans
    }

    private fun applySgr(
        params: String, color0: Int?, bold0: Boolean, italic0: Boolean, underline0: Boolean, strike0: Boolean,
    ): Span {
        var color = color0
        var bold = bold0
        var italic = italic0
        var underline = underline0
        var strike = strike0
        val codes = if (params.isEmpty()) listOf(0) else params.split(';').map { it.toIntOrNull() ?: 0 }
        var k = 0
        while (k < codes.size) {
            when (val n = codes[k]) {
                0 -> { color = null; bold = false; italic = false; underline = false; strike = false }
                1 -> bold = true
                3 -> italic = true
                4 -> underline = true
                9 -> strike = true
                22 -> bold = false
                23 -> italic = false
                24 -> underline = false
                29 -> strike = false
                39 -> color = null
                in 30..37 -> color = ANSI_STD[n - 30]
                in 90..97 -> color = ANSI_BRIGHT[n - 90]
                38 -> {
                    if (k + 2 < codes.size && codes[k + 1] == 5) {
                        color = xterm256(codes[k + 2])
                        k += 2
                    } else if (k + 4 < codes.size && codes[k + 1] == 2) {
                        color = ((codes[k + 2] and 255) shl 16) or ((codes[k + 3] and 255) shl 8) or (codes[k + 4] and 255)
                        k += 4
                    }
                }
            }
            k++
        }
        return Span("", color, bold, italic, underline, strike)
    }

    private fun xterm256(n: Int): Int = when {
        n < 0 -> 0xFFFFFF
        n < 8 -> ANSI_STD[n]
        n < 16 -> ANSI_BRIGHT[n - 8]
        n < 232 -> {
            val v = n - 16
            val steps = intArrayOf(0, 95, 135, 175, 215, 255)
            (steps[v / 36] shl 16) or (steps[(v / 6) % 6] shl 8) or steps[v % 6]
        }
        n < 256 -> {
            val g = 8 + (n - 232) * 10
            (g shl 16) or (g shl 8) or g
        }
        else -> 0xFFFFFF
    }

    private fun String.isHexColor(): Boolean = length == 6 && all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
}
