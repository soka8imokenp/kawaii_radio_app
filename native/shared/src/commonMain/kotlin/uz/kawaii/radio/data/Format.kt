@file:OptIn(kotlin.time.ExperimentalTime::class)

package uz.kawaii.radio.data

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.Instant

private val MONTHS = listOf("yanvar", "fevral", "mart", "aprel", "may", "iyun", "iyul", "avgust", "sentabr", "oktabr", "noyabr", "dekabr")

private fun two(n: Int) = n.toString().padStart(2, '0')

/** "bugun 19:02", "kecha 21:10" or "14-oktabr 2026, 20:31". */
fun formatWhen(ms: Long): String {
    if (ms <= 0) return "—"
    val tz = TimeZone.currentSystemDefault()
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    val today = Clock.System.now().toLocalDateTime(tz).date
    val time = "${two(t.hour)}:${two(t.minute)}"
    return when (today.toEpochDays() - t.date.toEpochDays()) {
        0L -> "bugun $time"
        1L -> "kecha $time"
        else -> "${t.day}-${MONTHS[t.month.ordinal]} ${t.year}, $time"
    }
}

/** "2026-10" for the month of [ms]. */
fun monthKey(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.year}-${two(t.month.ordinal + 1)}"
}

/** "OKTABR 2026" for a month key. */
fun monthTitle(key: String): String {
    val (y, m) = key.split('-').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
    return "${MONTHS[(m - 1).coerceIn(0, 11)].uppercase()} $y"
}

/** Short date for list rows: "14-okt". */
fun formatShortDate(ms: Long): String {
    if (ms <= 0) return ""
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.day}-${MONTHS[t.month.ordinal].take(3)}"
}

/** 3:07 */
fun formatDuration(seconds: Double): String {
    val v = seconds.toInt().coerceAtLeast(0)
    return "${v / 60}:${two(v % 60)}"
}

/** Listening time: "40 son", "45 daq" or "3 soat 12 daq". */
fun formatListening(seconds: Long): String {
    val m = seconds / 60
    return when {
        seconds < 60 -> "$seconds son"
        m < 60 -> "$m daq"
        else -> "${m / 60} soat ${m % 60} daq"
    }
}

/** The main artist of a credit line ("Hatsune Miku feat. Kasane Teto" → "Hatsune Miku"). */
fun mainArtist(artist: String): String =
    artist.split(Regex(""",| feat\.? | ft\.? | & | x """, RegexOption.IGNORE_CASE)).first().trim()

/** Popular J-pop / anime names offered in the "favourite artist" picker. */
val POPULAR_ARTISTS = listOf(
    "YOASOBI", "Ado", "Kenshi Yonezu", "Fujii Kaze", "Vaundy", "Official HIGE DANdism", "Mrs. GREEN APPLE",
    "King Gnu", "Aimer", "LiSA", "Eve", "RADWIMPS", "Yorushika", "ZUTOMAYO", "Creepy Nuts", "Hatsune Miku",
    "Aimyon", "Yuuri", "back number", "Hikaru Utada", "SEKAI NO OWARI", "Mafumafu", "Ayase", "DECO*27",
    "imase", "Atarashii Gakko!", "Tele", "ano", "Sakanaction", "Ryokuoushoku Shakai",
)
