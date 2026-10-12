package com.example.taylorlyrics.data

/** 单条曲目数据模型 */
data class Track(
    val track: String,
    val album: String,
    val trackNum: Int,
    val disc: Int,
    val albumRelease: String,
    val durationSec: Int,
    val hasSyncedLyrics: Boolean,
    val plainLyrics: String,
    val syncedLyrics: String,
    /** Spotify 总流媒体播放量（kworb 数据）；无数据为 0 */
    val streams: Long = 0L,
    /** 场景标签（如"深夜""心碎"等，中文）；无数据为空 */
    val tags: List<String> = emptyList(),
)

/** 同步歌词的一行：时间(毫秒) + 文本 */
data class SyncedLine(val timeMs: Int, val text: String)

/** 解析结果 */
data class LyricsResult(
    val track: Track? = null,
    val hits: List<Track> = emptyList(),
)

fun formatDuration(sec: Int): String {
    val m = sec / 60
    val s = sec % 60
    return "%d:%02d".format(m, s)
}

/**
 * 把 Spotify 总播放量格式化为可读字符串。
 * 例：3,508,616,612 -> "3.5B"；486,630,446 -> "486.6M"；172,615,884 -> "172.6M"；999 -> "999"
 * @return 无数据（<=0）时返回空字符串
 */
fun formatStreams(v: Long): String {
    if (v <= 0L) return ""
    return when {
        v >= 1_000_000_000L -> {
            val b = v.toDouble() / 1_000_000_000.0
            "%.1fB".format(b)
        }
        v >= 1_000_000L -> {
            val m = v.toDouble() / 1_000_000.0
            "%.1fM".format(m)
        }
        v >= 1_000L -> {
            val k = v.toDouble() / 1_000.0
            "%.1fK".format(k)
        }
        else -> v.toString()
    }
}

/**
 * 解析 LRC 同步歌词，如 "[00:14.93] vintage tee, brand new phone"
 * 返回按时间排序的行列表
 */
fun parseSyncedLyrics(lrc: String): List<SyncedLine> {
    val out = ArrayList<SyncedLine>()
    for (line in lrc.lines()) {
        val s = line.trim()
        if (s.isEmpty()) continue
        if (!s.startsWith("[")) continue
        // 提取所有 [mm:ss.xx] 时间戳
        val times = ArrayList<Int>()
        var idx = 0
        while (true) {
            val open = s.indexOf('[', idx)
            if (open < 0) break
            val close = s.indexOf(']', open)
            if (close < 0) break
            val ts = s.substring(open + 1, close)
            idx = close + 1
            val ms = parseTimestamp(ts)
            if (ms != null) times.add(ms)
        }
        // 文本在最后一个 ] 之后
        val lastClose = s.lastIndexOf(']')
        val text = if (lastClose >= 0) s.substring(lastClose + 1).trim() else ""
        if (text.isEmpty()) continue
        for (t in times) out.add(SyncedLine(t, text))
    }
    return out.sortedBy { it.timeMs }
}

private fun parseTimestamp(ts: String): Int? {
    val parts = ts.split(":")
    if (parts.size < 2) return null
    val min = parts[parts.size - 2].toIntOrNull() ?: return null
    val secStr = parts[parts.size - 1]
    val secDot = secStr.indexOf('.')
    val sec = if (secDot >= 0) secStr.substring(0, secDot).toIntOrNull()
        ?: return null else secStr.toIntOrNull() ?: return null
    var frac = 0
    if (secDot >= 0) {
        val f = secStr.substring(secDot + 1)
        frac = if (f.length >= 3) f.substring(0, 3).toIntOrNull() ?: 0
        else (f + "00").substring(0, 3).toIntOrNull() ?: 0
    }
    return ((min * 60) + sec) * 1000 + frac
}

// ======================= 歌词分段（v1.3） =======================

/** 歌词的一个段落：数据源没有 [Bridge]/[Verse] 标记，按空行划分 */
data class Section(
    val index: Int,      // 段序号（从 0 开始）
    val title: String,   // "第 N 段"
    val firstLine: Int,  // 起始行在 lines 里的下标
    val lineCount: Int,  // 段内非空行数
    val preview: String, // 首行截断预览
)

/** 按空行把歌词行切成分段；返回按出现顺序排列（Bridge 等靠后的段在列表尾部） */
fun splitSections(lines: List<LineText>): List<Section> {
    if (lines.isEmpty()) return emptyList()
    val out = ArrayList<Section>(12)
    var start = -1
    var count = 0

    fun flush() {
        if (start < 0) return
        val first = lines[start].text.trim()
        val preview = if (first.length > 22) first.take(22) + "…" else first
        out.add(Section(out.size, "第 ${out.size + 1} 段", start, count, preview))
        start = -1
        count = 0
    }

    for (i in lines.indices) {
        if (lines[i].text.isBlank()) {
            if (start >= 0) flush()
        } else {
            if (start < 0) start = i
            count++
        }
    }
    flush()
    return out
}