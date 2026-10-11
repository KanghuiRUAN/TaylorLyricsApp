package com.example.taylorlyrics.data

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle

/** 归一化：小写 + 去除所有非字母数字字符 */
fun normalize(s: String): String =
    s.lowercase().filter { it.isLetterOrDigit() }

/** 高亮色：琥珀金 */
val MarkColor = Color(0xFFB87333)

/**
 * 归一化文本 + 下标映射：map[i] = 归一化后第 i 个字符在原文中的下标。
 * 用 IntArray 而不是 List<Int>，避免 7.6MB 文本上的百万级装箱。
 */
class Norm(val text: String, val map: IntArray)

fun normOf(s: String): Norm {
    val sb = StringBuilder(s.length)
    val map = IntArray(s.length)
    var k = 0
    for (i in s.indices) {
        val c = s[i]
        if (c.isLetterOrDigit()) {
            sb.append(c.lowercaseChar())
            map[k] = i
            k++
        }
    }
    return if (k == s.length) Norm(sb.toString(), map) else Norm(sb.toString(), map.copyOf(k))
}

/**
 * 在已归一化的语料里找 q（q 也须已归一化），返回原文的字符区间。
 * 线性复杂度；相邻匹配不重叠。
 */
fun normRanges(n: Norm, q: String): List<IntRange> {
    if (q.isEmpty() || n.text.length < q.length) return emptyList()
    val out = ArrayList<IntRange>(4)
    var pos = 0
    while (pos <= n.text.length - q.length) {
        val f = n.text.indexOf(q, pos)
        if (f < 0) break
        val start = n.map[f]
        val end = n.map[f + q.length - 1] + 1
        out.add(start until end)
        pos = f + q.length
    }
    return out
}

/** 便捷版：直接对原文查找 */
fun matchRanges(text: String, term: String): List<IntRange> {
    val q = normalize(term.trim())
    if (q.isEmpty()) return emptyList()
    return normRanges(normOf(text), q)
}

/** 把全文区间裁剪并平移到某个局部片段（行 / 摘要）的坐标系 */
fun clipRanges(ranges: List<IntRange>, from: Int, to: Int): List<IntRange> {
    if (ranges.isEmpty() || from >= to) return emptyList()
    val out = ArrayList<IntRange>(4)
    for (r in ranges) {
        if (r.last < from) continue
        if (r.first >= to) break
        val s = maxOf(r.first, from)
        val e = minOf(r.last + 1, to) - 1
        if (e >= s) out.add((s - from)..(e - from))
    }
    return out
}

/** 歌词按行拆分，保留每行在全文中的起始下标（用于跳转定位） */
class LineText(val text: String, val start: Int)

fun splitLines(body: String): List<LineText> {
    val out = ArrayList<LineText>(64)
    var start = 0
    var i = 0
    while (i <= body.length) {
        if (i == body.length || body[i] == '\n') {
            out.add(LineText(body.substring(start, i), start))
            start = i + 1
        }
        i++
    }
    return out
}

/** 摘要（关键词前后各取 radius 个字符）+ 摘要内的局部高亮区间 */
class Snippet(val text: String, val ranges: List<IntRange>, val total: Int, val truncated: Boolean)

fun snippetOf(body: String, ranges: List<IntRange>, radius: Int = 44): Snippet? {
    if (ranges.isEmpty()) return null
    val first = ranges.first()
    val from = (first.first - radius).coerceAtLeast(0)
    val to = (first.last + 1 + radius).coerceAtMost(body.length)
    val slice = body.substring(from, to)
    val raw = slice.replace("\n", " ").trim()
    val pad = slice.length - slice.trimStart().length
    val clipped = clipRanges(ranges, from + pad, to)
    // 前面拼了省略号时，高亮区间要整体右移一格
    val shifted = if (from > 0) clipped.map { (it.first + 1)..(it.last + 1) } else clipped
    return Snippet(
        text = if (from > 0) "…" + raw else raw,
        ranges = shifted,
        total = ranges.size,
        truncated = from > 0 || to < body.length,
    )
}

/** 按已算好的区间上色；区间为空时返回原样字符串 */
fun highlightWith(text: String, ranges: List<IntRange>): AnnotatedString {
    if (ranges.isEmpty()) return AnnotatedString(text)
    return buildAnnotatedString {
        var cursor = 0
        for (r in ranges) {
            val s = r.first.coerceAtLeast(cursor).coerceAtMost(text.length)
            val e = (r.last + 1).coerceAtMost(text.length)
            if (e <= s) continue
            if (s > cursor) append(text.substring(cursor, s))
            withStyle(SpanStyle(color = MarkColor, fontWeight = FontWeight.Bold)) {
                append(text.substring(s, e))
            }
            cursor = e
        }
        if (cursor < text.length) append(text.substring(cursor))
    }
}