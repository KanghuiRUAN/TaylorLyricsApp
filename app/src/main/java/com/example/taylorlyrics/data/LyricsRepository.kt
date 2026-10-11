package com.example.taylorlyrics.data

import android.content.Context
import org.json.JSONObject

/** 专辑信息 */
data class AlbumInfo(
    val name: String,
    val release: String,
    val tracks: List<Track>,
)

/**
 * 歌词资料库：从 assets 中读取内置 JSON 并缓存到内存，
 * 提供专辑浏览、歌曲搜索、歌词全文搜索。
 */
class LyricsRepository private constructor(private val allTracks: List<Track>) {

    private val albums: List<AlbumInfo> by lazy {
        allTracks
            .groupBy { it.album }
            .map { (name, list) ->
                AlbumInfo(name, list.firstOrNull()?.albumRelease.orEmpty(),
                    list.sortedBy { it.disc * 1000 + it.trackNum })
            }
            .sortedBy { it.release }
    }

    val trackCount: Int get() = allTracks.size
    val albumCount: Int get() = albums.size

    fun allAlbums(): List<AlbumInfo> = albums

    fun albumTracks(albumName: String): List<Track> =
        allTracks.filter { it.album == albumName }
            .sortedBy { it.disc * 1000 + it.trackNum }

    /** 预先归一化歌名与歌词全文，避免每次按键都重算 7.6MB 文本（否则卡顿甚至 ANR） */
    private class Row(val track: Track, val nTitle: String, val nBody: String)

    private val rows: List<Row> by lazy {
        allTracks.map { Row(it, normalize(it.track), normalize(it.plainLyrics)) }
    }

    /**
     * 搜索：优先按歌名匹配，其次按歌词全文匹配。
     * query 大小写不敏感、忽略常见干扰字符。
     */
    fun search(query: String): List<Track> {
        val q = normalize(query)
        if (q.isEmpty()) return emptyList()
        val titleHits = ArrayList<Track>()
        val bodyHits = ArrayList<Track>()
        for (r in rows) {
            if (r.nTitle.contains(q)) titleHits.add(r.track)
            else if (r.nBody.contains(q)) bodyHits.add(r.track)
        }
        return titleHits + bodyHits
    }

    /** 从某个专辑中查找指定曲目（用于头像点击后的详情） */
    fun findInAlbum(album: String, trackNum: Int, disc: Int = 1): Track? =
        allTracks.firstOrNull { it.album == album && it.disc == disc && it.trackNum == trackNum }

    companion object {
        private var instance: LyricsRepository? = null

        @Synchronized
        fun get(context: Context): LyricsRepository {
            instance?.let { return it }
            val tracks = loadAssets(context)
                .let { LyricsRepository(it) }
            instance = tracks
            return tracks
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

/** 归一化：小写 + 去除所有非字母数字字符 */
fun normalize(s: String): String =
    s.lowercase().filter { it.isLetterOrDigit() }