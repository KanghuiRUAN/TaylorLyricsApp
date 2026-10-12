package com.example.taylorlyrics.data

import android.content.Context
import org.json.JSONObject
import java.util.concurrent.Executors
import kotlin.random.Random

/** 专辑信息 */
data class AlbumInfo(
    val name: String,
    val release: String,
    val tracks: List<Track>,
)

/** 一条搜索命中：曲目 + 是否歌名命中 + 歌词内命中区间（track.plainLyrics 坐标系）+ 摘要片段 */
class Hit(
    val track: Track,
    val inTitle: Boolean,
    val ranges: List<IntRange>,
    val snip: Snippet?,
)

/**
 * 歌词资料库：从 assets 读取内置 JSON 并常驻内存，提供
 *  · 专辑浏览
 *  · 搜索（歌名优先 + 歌词命中位置 + 摘要）
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
                    list.sortedByDescending { it.streams },
                )
            }
            .sortedByDescending { it.tracks.maxOfOrNull(Track::streams) ?: 0L }
    }

    fun allAlbums(): List<AlbumInfo> = albumList
    val albumCount: Int get() = albumList.size

    fun albumTracks(albumName: String): List<Track> =
        allTracks.filter { it.album == albumName }
            .sortedByDescending { it.streams }

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
            if (i in hiddenIndices) continue   // v1.5: 隐藏重复版本
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
            if (row in hiddenIndices) continue   // v1.5: 隐藏重复版本
            if (seenTitle.contains(row)) continue
            seenTitle.add(row)
            val rg = rangesOfRow(bi, row, q, 120)
            val t = allTracks[row]
            bodyHits.add(Hit(t, false, rg, snippetOf(t.plainLyrics, rg)))
        }
        val merged = titleHits + bodyHits
        return if (merged.size > 1)
            merged.sortedByDescending { it.track.streams }
        else merged
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

    // ---------------- v1.3 粉丝功能 ----------------

    // ---------------- v1.5 多版本去重 ----------------

    /** 是否 Taylor's Version 重录版（兼容 ASCII ' 与弯引号 ' 两种写法） */
    private fun isTaylorsVersion(t: Track): Boolean {
        val n = t.track
        return (n.contains("Taylor's", true) || n.contains("Taylor\u2019s", true)) &&
            n.contains("Version", true)
    }

    /** 去掉所有括号版本标记后的基础歌名（循环剥离，兼容嵌套括号如 (Piano Version (From ".."))） */
    private fun baseNameOf(name: String): String {
        var s = name
        while (true) {
            val o = s.indexOf('(')
            val c = s.lastIndexOf(')')
            if (o < 0 || c < o) break
            s = s.removeRange(o, c + 1)
            s = s.replace(Regex("\\s{2,}"), " ").trim()
        }
        return s.trim()
    }

    /**
     * 需要从【搜索 / 随便来一首 / 猜歌词】中隐藏的曲目下标。
     * 规则：同一首歌（基础名相同）且歌词完全一致时，视为重复版本，
     * 只保留一个代表版本（优先 Taylor's Version，其次保留播放量最高者），其余全部隐藏。
     */
    private val hiddenIndices: Set<Int> by lazy {
        val byKey = HashMap<Pair<String, String>, MutableList<Int>>()
        for (i in allTracks.indices) {
            val t = allTracks[i]
            val key = baseNameOf(t.track) to t.plainLyrics
            byKey.getOrPut(key) { ArrayList() }.add(i)
        }
        val hidden = HashSet<Int>()
        for (entries in byKey.values) {
            if (entries.size < 2) continue
            val rep = entries.maxWith(
                compareBy(
                    // TV 判 1、非 TV 判 0：maxWith 取 key 最大者，故 Taylor's Version 优先；
                    // 同为 TV（或同为非 TV）时再比较播放量，streams 高者优先
                    { if (isTaylorsVersion(allTracks[it])) 1 else 0 },
                    { allTracks[it].streams },
                )
            )
            for (i in entries) if (i != rep) hidden.add(i)
        }
        hidden
    }

    /** 可见曲目（已去掉重复版本） */
    private val visibleTracks: List<Track> by lazy {
        allTracks.filterIndexed { i, _ -> i !in hiddenIndices }
    }

    /** 随机惊喜：随便来一首（仅在去重后的可见曲目里取） */
    fun randomTrack(): Track = visibleTracks[Random.nextInt(visibleTracks.size)]

    /** 随机抽取 n 首不同的歌（猜歌词出题用；优先取热度较高的歌，仅在去重后的可见曲目里取） */
    fun sampleTracks(n: Int): List<Track> =
        visibleTracks.shuffled().sortedByDescending { it.streams }.take(n)

    /** 收藏 key -> 曲目（Favorites 存的是字符串 key，返回时反查实体；按热度降序） */
    fun tracksByKeys(keys: List<String>): List<Track> =
        keys.mapNotNull { k ->
            val p = k.split('\u0001')
            if (p.size == 4) findTrack(p[0], p[2].toIntOrNull() ?: -1, p[1].toIntOrNull() ?: -1)
            else null
        }.sortedByDescending { it.streams }

    /**
     * 短语级搜索：在歌词正文里按词序逐词连续匹配输入短语（标点和空格不影响），
     * 例如搜 "love story" 能命中 "love story, baby, just say, yes"。
     */
    fun searchPhrase(query: String, limit: Int = 100): List<Hit> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val nq = normalize(q)
        // 逐歌独立查找：bodyIndex 是拼接大文本，直接在里面找短语会跨歌误命中
        val out = ArrayList<Hit>(16)
        for ((i, t) in allTracks.withIndex()) {
            if (out.size >= limit) break
            if (i in hiddenIndices) continue   // v1.5: 隐藏重复版本
            val rg = normRanges(normOf(t.plainLyrics), nq)
            if (rg.isEmpty()) continue
            out.add(Hit(t, normalize(t.track).contains(nq), rg.take(120), snippetOf(t.plainLyrics, rg)))
        }
        return out.sortedByDescending { it.track.streams }
    }


    /** 整张专辑收藏/取消：把该专辑全部曲目一起加入或移出收藏，返回切换后是否已收藏 */
    fun toggleAlbumFav(c: Context, albumName: String): Boolean {
        val ts = albumTracks(albumName)
        if (ts.isEmpty()) return false
        val target = !Favorites.isFav(c, ts[0])
        for (t in ts) if (Favorites.isFav(c, t) != target) Favorites.toggle(c, t)
        return target
    }

    // ---------------- 生命周期 ----------------

    private val warmExecutor = Executors.newSingleThreadExecutor()

    /** 启动后在后台线程预热索引，避免用户第一次搜索时卡顿 */
    fun warmUp() {
        warmExecutor.execute {
            try {
                rows; bodyIndex
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

            // 热度 & 场景标签：读取 assets 侧 hotness.json（kworb 播放量 + 自动分类标签）
            val hot = HashMap<String, Pair<Long, List<String>>>()
            try {
                val ht = context.assets.open("hotness.json")
                    .bufferedReader(Charsets.UTF_8).use { it.readText() }
                val hotRoot = org.json.JSONArray(ht)
                for (i in 0 until hotRoot.length()) {
                    val o = hotRoot.getJSONObject(i)
                    val key = o.optString("album") + "\u0000" + o.optString("track")
                    val tags = ArrayList<String>()
                    val ta = o.optJSONArray("tags")
                    if (ta != null) for (j in 0 until ta.length()) tags.add(ta.optString(j))
                    hot[key] = Pair(o.optLong("streams", 0L), tags)
                }
            } catch (_: Throwable) {
            }

            val list = ArrayList<Track>(arr.length())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val key = o.optString("album") + "\u0000" + o.optString("track")
                val h = hot[key]
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
                        streams = h?.first ?: 0L,
                        tags = h?.second ?: emptyList(),
                    )
                )
            }
            return list
        }
    }
}
