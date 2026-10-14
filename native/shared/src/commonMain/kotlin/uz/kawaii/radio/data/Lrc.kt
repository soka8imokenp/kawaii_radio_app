package uz.kawaii.radio.data

data class LyricLine(val t: Double, val text: String)

private val LINE = Regex("""^\[(\d+):(\d+(?:\.\d+)?)]\s*(.*)$""")

/** Parses `[mm:ss.xx] text` lines. Lines without a timestamp are ignored. */
fun parseLrc(src: String): List<LyricLine> =
    src.lineSequence()
        .mapNotNull { raw ->
            LINE.find(raw.trimEnd('\r'))?.let { m ->
                LyricLine(m.groupValues[1].toDouble() * 60 + m.groupValues[2].toDouble(), m.groupValues[3].trim())
            }
        }
        .sortedBy { it.t }
        .toList()

/** Index of the line being sung at [time] seconds, -1 before the first one. */
fun lineAt(lines: List<LyricLine>, time: Double): Int {
    var lo = 0
    var hi = lines.size - 1
    var found = -1
    while (lo <= hi) {
        val mid = (lo + hi) ushr 1
        if (lines[mid].t <= time) { found = mid; lo = mid + 1 } else hi = mid - 1
    }
    return found
}
