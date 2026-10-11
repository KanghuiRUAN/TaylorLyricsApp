package com.example.taylorlyrics.data

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors

/** 专辑信息 */
data class AlbumInfo(
    val name: String,
    val release: String,
    val tracks: List<Track>,
)

/**
 * 一条搜索命中：曲目 + 是否歌名命中 + 歌词内命中区间（track.plainLyrics 坐标系）+ 摘要片段
 */
class Hit(
    val track: Track,
    val inTitle: Boolean,
    val ranges: List<IntRange>,
    val snip: Snippet?,
)

/** 词网络节点：词 / 出现在多少首歌 / 全库总出现次数 */
class WordNode(val word: String, val songs: Int, val total: Int)

/**
 * 歌词资料库：从 assets 读取内置 JSON 并常驻内存，提供
 *  · 专辑浏览
 *  · 搜索（歌名优先 + 歌词命中位置 + 摘要）
 *  · 词网络（词频、跨专辑共现、按词下钻歌曲）
 *
 * 性能要点：7.6MB 语料只在后台线程构建一次索引；索引用"一整条归一化字符串 +
 * IntArray 偏移表"表示，避免在千万级字符上做 String 拷贝与 Integer 装箱。
 */
class LyricsRepository private constructor(private val allTracks: List<Track>) {

    val trackCount: Int get() = allTracks.size

    // ---------------- 索引 ----------------

    private class Row(val track: Track, val nTitle: Norm)

    /** 全部歌词拼成一条归一化文本；map 记录每个归一化字符在其所属歌词内的下标 */
    private class BodyIndex(
        val norm: String,
        val map: IntArray,
        val starts: IntArray,   // 每首歌在 norm 中的起点
        val lens: IntArray,     // 每首歌在 norm 中的长度
    )

    private val rows: List<Row> by lazy {
        allTracks.map { Row(it, normOf(it.track)) }
    }

    private val bodyIndex: BodyIndex by lazy {
        val tracks = allTracks
        val n = tracks.size
        // 第一遍：统计每首歌归一化后的长度，得到总长，用于精确分配数组（避免 Integer 装箱）
        val lens = IntArray(n)
        var total = 0
        for (i in 0 until n) {
            val body = tracks[i].plainLyrics
            var c = 0
            var k = 0
            while (k < body.length) {
                if (body[k].isLetterOrDigit()) c++
                k++
            }
            lens[i] = c
            total += c
        }
        val starts = IntArray(n)
        var acc = 0
        for (i in 0 until n) {
            starts[i] = acc
            acc += lens[i]
        }
        // 第二遍：填充字符与偏移
        val chars = CharArray(total)
        val map = IntArray(total)
        for (i in 0 until n) {
            val body = tracks[i].plainLyrics
            var w = starts[i]
            var k = 0
            while (k < body.length) {
                val ch = body[k]
                if (ch.isLetterOrDigit()) {
                    chars[w] = ch.lowercaseChar()
                    map[w] = k
                    w++
                }
                k++
            }
        }
        BodyIndex(String(chars), map, starts, lens)
    }

    private val albumList: List<AlbumInfo> by lazy {
        allTracks
            .groupBy { it.album }
            .map { (name, list) ->
                AlbumInfo(
                    name,
                    list.firstOrNull()?.albumRelease.orEmpty(),
                    list.sortedBy { it.disc * 1000 + it.trackNum },
                )
            }
            .sortedBy { it.release }
    }

    fun allAlbums(): List<AlbumInfo> = albumList
    val albumCount: Int get() = albumList.size

    fun albumTracks(albumName: String): List<Track> =
        allTracks.filter { it.album == albumName }
            .sortedBy { it.disc * 1000 + it.trackNum }

    // ---------------- 搜索 ----------------

    /**
     * 搜索：歌名命中优先，其次歌词命中。
     * 歌词命中会带回全部出现位置（供高亮）以及第一段上下文摘要。
     */
    fun search(query: String, limit: Int = 150): List<Hit> {
        val q = normalize(query.trim())
        if (q.isEmpty()) return emptyList()

        val bi = bodyIndex
        val titleHits = ArrayList<Hit>(16)
        val bodyHits = ArrayList<Hit>(64)
        val seenTitle = HashSet<Int>(32)

        for ((i, r) in rows.withIndex()) {
            if (!r.nTitle.text.contains(q)) continue
            seenTitle.add(i)
            val rg = rangesOfRow(bi, i, q, 120)
            titleHits.add(Hit(r.track, true, rg, snippetOf(r.track.plainLyrics, rg)))
            if (titleHits.size >= limit) return titleHits
        }

        var pos = 0
        while (pos <= bi.norm.length - q.length && bodyHits.size < limit) {
            val f = bi.norm.indexOf(q, pos)
            if (f < 0) break
            pos = f + q.length
            val row = rowOf(bi, f)
            if (row < 0) continue
            // 跨歌边界产生的伪命中
            if (f + q.length > bi.starts[row] + bi.lens[row]) continue
            if (seenTitle.contains(row)) continue
            seenTitle.add(row)
            val rg = rangesOfRow(bi, row, q, 120)
            val t = allTracks[row]
            bodyHits.add(Hit(t, false, rg, snippetOf(t.plainLyrics, rg)))
        }
        return titleHits + bodyHits
    }

    private fun rowOf(bi: BodyIndex, g: Int): Int {
        var lo = 0
        var hi = bi.starts.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = bi.starts[mid]
            if (g < s) hi = mid - 1 else if (g >= s + bi.lens[mid]) lo = mid + 1 else return mid
        }
        return -1
    }

    private fun rangesOfRow(bi: BodyIndex, row: Int, q: String, cap: Int): List<IntRange> {
        val from = bi.starts[row]
        val to = from + bi.lens[row]
        val out = ArrayList<IntRange>(4)
        var pos = from
        // String.indexOf 没有 (String, Int, Int) 重载，这里查找后再判断是否越出本歌边界
        while (pos <= to - q.length) {
            val f = bi.norm.indexOf(q, pos)
            if (f < 0 || f + q.length > to) break
            out.add(bi.map[f]..bi.map[f + q.length - 1])
            pos = f + q.length
            if (out.size >= cap) break
        }
        return out
    }

    fun findTrack(album: String, trackNum: Int, disc: Int): Track? =
        allTracks.firstOrNull { it.album == album && it.disc == disc && it.trackNum == trackNum }

    // ---------------- 词网络 ----------------

    private class WordIndex(
        val perSong: List<HashSet<String>>,
        val docFreq: HashMap<String, Int>,
        val totalFreq: HashMap<String, Int>,
    )

    private val wordIndex: WordIndex by lazy {
        val perSong = ArrayList<HashSet<String>>(allTracks.size)
        val docs = HashMap<String, Int>(16384)
        val tot = HashMap<String, Int>(16384)
        val vocab = HashMap<String, String>(16384)   // 复用同一个 String 实例，省内存
        val sb = StringBuilder(24)
        for (t in allTracks) {
            val body = t.plainLyrics
            val cnt = HashMap<String, Int>(256)
            var i = 0
            while (i <= body.length) {
                val c = if (i < body.length) body[i] else ' '
                if (c.isLetter()) sb.append(c.lowercaseChar()) else {
                    if (sb.length >= 4) {
                        var w = sb.toString()
                        vocab[w]?.let { w = it } ?: vocab.put(w, w)
                        cnt[w] = (cnt[w] ?: 0) + 1
                    }
                    sb.setLength(0)
                }
                i++
            }
            val set = HashSet<String>(cnt.size)
            set.addAll(cnt.keys)
            perSong.add(set)
            for (w in set) docs[w] = (docs[w] ?: 0) + 1
            for ((w, n) in cnt) tot[w] = (tot[w] ?: 0) + n
        }
        WordIndex(perSong, docs, tot)
    }

    /** 全库最常出现的词（词网络入口的枢纽节点） */
    fun topWords(limit: Int = 40): List<WordNode> {
        val idx = wordIndex
        return idx.docFreq.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        ).take(limit).map { WordNode(it.key, it.value, idx.totalFreq[it.key] ?: it.value) }
    }

    /** 某首歌自身的高频词 */
    fun topWordsIn(track: Track, limit: Int = 18): List<WordNode> {
        val idx = wordIndex
        val body = track.plainLyrics
        val cnt = HashMap<String, Int>(128)
        val sb = StringBuilder(24)
        var i = 0
        while (i <= body.length) {
            val c = if (i < body.length) body[i] else ' '
            if (c.isLetter()) sb.append(c.lowercaseChar()) else {
                if (sb.length >= 4) {
                    val w = sb.toString()
                    cnt[w] = (cnt[w] ?: 0) + 1
                }
                sb.setLength(0)
            }
            i++
        }
        return cnt.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        ).take(limit).map { WordNode(it.key, idx.docFreq[it.key] ?: 0, it.value) }
    }

    /** 与某个词最常"同处一首歌"的词 —— 词网络的边 */
    fun relatedWords(word: String, limit: Int = 40): List<WordNode> {
        val w = normalize(word.trim())
        if (w.isEmpty()) return emptyList()
        val idx = wordIndex
        val key = idx.docFreq.keys.firstOrNull { it == w } ?: w
        val score = HashMap<String, Int>(1024)
        for (other in idx.perSong) {
            if (other.isEmpty() || !other.contains(key)) continue
            for (o in other) if (o != key) score[o] = (score[o] ?: 0) + 1
        }
        return score.entries.sortedWith(
            compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }
        ).take(limit).map { WordNode(it.key, idx.docFreq[it.key] ?: 0, idx.totalFreq[it.key] ?: 0) }
    }

    /** 歌词含某个词的歌曲（点击节点后下钻） */
    fun songsContaining(word: String, limit: Int = 200): List<Hit> {
        val q = normalize(word.trim())
        if (q.isEmpty()) return emptyList()
        val bi = bodyIndex
        val out = ArrayList<Hit>(32)
        var pos = 0
        val done = HashSet<Int>(32)
        while (pos <= bi.norm.length - q.length && out.size < limit) {
            val f = bi.norm.indexOf(q, pos)
            if (f < 0) break
            pos = f + q.length
            val row = rowOf(bi, f)
            if (row < 0 || done.contains(row)) continue
            if (f + q.length > bi.starts[row] + bi.lens[row]) continue
            done.add(row)
            val rg = rangesOfRow(bi, row, q, 40)
            val t = allTracks[row]
            out.add(Hit(t, false, rg, snippetOf(t.plainLyrics, rg)))
        }
        return out
    }

    /** 词表规模（调试/展示用） */
    val vocabularySize: Int get() = wordIndex.docFreq.size

    // ---------------- 生命周期 ----------------

    private val warmExecutor = Executors.newSingleThreadExecutor()

    /** 启动后在后台线程预热索引，避免用户第一次搜索/打开词网络时卡顿 */
    fun warmUp() {
        warmExecutor.execute {
            try {
                rows; bodyIndex; wordIndex
            } catch (_: Throwable) {
            }
        }
    }

    companion object {
        private var instance: LyricsRepository? = null

        @Synchronized
        fun get(context: Context): LyricsRepository {
            instance?.let { return it }
            val repo = LyricsRepository(loadAssets(context))
            instance = repo
            return repo
        }

        private fun loadAssets(context: Context): List<Track> {
            val jsonText = context.assets.open("taylor_lyrics.json")
                .bufferedReader(Charsets.UTF_8).use { it.readText() }
            val root = JSONObject(jsonText)
            val arr = root.getJSONArray("tracks")
            val list = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(
                    Track(
                        track = o.optString("track"),
                        album = o.optString("album"),
                        trackNum = o.optInt("trackNum"),
                        disc = o.optInt("disc", 1),
                        albumRelease = o.optString("albumRelease"),
                        durationSec = o.optInt("durationSec"),
                        hasSyncedLyrics = o.optBoolean("hasSyncedLyrics"),
                        plainLyrics = o.optString("plainLyrics"),
                        syncedLyrics = o.optString("syncedLyrics"),
                    )
                )
            }
            return list
        }
    }
}
