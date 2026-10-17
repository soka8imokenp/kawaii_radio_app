package uz.kawaii.radio.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration

/**
 * Telegram-like chat formatting, kept as marks inside the text so old app versions and pushes still
 * read it: **bold**, __italic__, ++underline++, ~~strike~~, `code`, ||spoiler||.
 */
enum class Fmt(val mark: String, val label: String) {
    BOLD("**", "B"), ITALIC("__", "I"), UNDER("++", "U"), STRIKE("~~", "S"), CODE("`", "</>"), SPOILER("||", "▒"),
}

/** One formatted piece: the opening mark starts at [open], the closing one at [close]. */
private data class FmtSpan(val fmt: Fmt, val open: Int, val close: Int) {
    val from get() = open + fmt.mark.length
}

private fun parse(s: String): List<FmtSpan> {
    val spans = ArrayList<FmtSpan>()
    val openAt = HashMap<Fmt, Int>()
    var i = 0
    while (i < s.length) {
        // inside `code` nothing else is a mark
        val code = openAt[Fmt.CODE]
        if (code != null) {
            if (s[i] == '`') { if (i > code + 1) spans += FmtSpan(Fmt.CODE, code, i); openAt.remove(Fmt.CODE) }
            i++
            continue
        }
        val f = Fmt.entries.firstOrNull { s.startsWith(it.mark, i) }
        if (f == null) { i++; continue }
        val o = openAt.remove(f)
        if (o == null) openAt[f] = i
        else if (i > o + f.mark.length) spans += FmtSpan(f, o, i)
        i += f.mark.length
    }
    return spans
}

private fun style(f: Fmt, ink: Color, revealed: Boolean): SpanStyle = when (f) {
    Fmt.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
    Fmt.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
    Fmt.UNDER -> SpanStyle(textDecoration = TextDecoration.Underline)
    Fmt.STRIKE -> SpanStyle(textDecoration = TextDecoration.LineThrough)
    Fmt.CODE -> SpanStyle(fontFamily = FontFamily.Monospace, background = ink.copy(alpha = 0.12f))
    // a spoiler is a solid bar of ink until it is tapped
    Fmt.SPOILER -> if (revealed) SpanStyle(background = ink.copy(alpha = 0.12f)) else SpanStyle(color = Color.Transparent, background = ink.copy(alpha = 0.85f))
}

/** True when the text has a ||spoiler|| (the first tap on the bubble reveals it). */
fun hasSpoiler(s: String) = parse(s).any { it.fmt == Fmt.SPOILER }

/** The message as shown in a bubble: marks gone, their styles applied. */
fun formatChat(s: String, ink: Color, revealed: Boolean = false): AnnotatedString {
    val spans = parse(s)
    if (spans.isEmpty()) return AnnotatedString(s)
    val hidden = BooleanArray(s.length)
    for (sp in spans) {
        for (k in sp.open until sp.from) hidden[k] = true
        for (k in sp.close until sp.close + sp.fmt.mark.length) hidden[k] = true
    }
    // position in the shown text for every position of the raw text
    val at = IntArray(s.length + 1)
    var n = 0
    for (k in s.indices) { at[k] = n; if (!hidden[k]) n++ }
    at[s.length] = n
    return buildAnnotatedString {
        for (k in s.indices) if (!hidden[k]) append(s[k])
        for (sp in spans) addStyle(style(sp.fmt, ink, revealed), at[sp.from], at[sp.close])
    }
}

/** Plain text without marks (previews, replies, notifications). */
fun stripFormat(s: String): String = formatChat(s, Color.Black, revealed = true).text

/**
 * The composer keeps the marks (so the cursor never jumps) but shows them faint and the text
 * between them already styled, like a live preview.
 */
class ChatFormatTransformation(private val ink: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val s = text.text
        val spans = parse(s)
        if (spans.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val out = buildAnnotatedString {
            append(s)
            for (sp in spans) {
                addStyle(style(sp.fmt, ink, revealed = true), sp.from, sp.close)
                addStyle(SpanStyle(color = ink.copy(alpha = 0.35f)), sp.open, sp.from)
                addStyle(SpanStyle(color = ink.copy(alpha = 0.35f)), sp.close, sp.close + sp.fmt.mark.length)
            }
        }
        return TransformedText(out, OffsetMapping.Identity)
    }
}

/**
 * Format key in the composer: wraps the selection in [f]'s marks, or takes them away when the
 * selection is already wrapped. With nothing selected it drops an empty pair and puts the cursor inside.
 */
fun TextFieldValue.toggle(f: Fmt): TextFieldValue {
    val m = f.mark
    val a = selection.min
    val b = selection.max
    val t = text
    // already wrapped (marks just outside, or included in the selection): unwrap
    if (a >= m.length && b + m.length <= t.length && t.substring(a - m.length, a) == m && t.substring(b, b + m.length) == m) {
        val nt = t.removeRange(b, b + m.length).removeRange(a - m.length, a)
        return copy(text = nt, selection = TextRange(a - m.length, b - m.length))
    }
    val sel = t.substring(a, b)
    if (sel.length >= 2 * m.length && sel.startsWith(m) && sel.endsWith(m)) {
        val inner = sel.substring(m.length, sel.length - m.length)
        return copy(text = t.replaceRange(a, b, inner), selection = TextRange(a, a + inner.length))
    }
    val nt = t.substring(0, a) + m + sel + m + t.substring(b)
    return copy(text = nt, selection = if (a == b) TextRange(a + m.length) else TextRange(a + m.length, b + m.length))
}
