package com.example.taylorlyrics.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一轮猜歌词的成绩记录 */
data class GuessRecord(
    val time: Long,
    val correct: Int,
    val total: Int,
)

/**
 * 猜歌词历史计分板：轻量本地存储，直接读写 filesDir 下的 JSON 文件。
 * 设计风格与 Favorites 保持一致（零依赖、单例、懒加载）。
 * 记录以「最新在前」保存，最多保留 MAX 条。
 */
object GuessScores {
    private const val FILE = "guess_scores.json"
    private const val MAX = 50

    private val records = ArrayList<GuessRecord>()
    private var loaded = false

    @Synchronized
    private fun file(c: Context) = File(c.filesDir, FILE)

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    records.add(
                        GuessRecord(
                            time = o.optLong("time", 0L),
                            correct = o.optInt("correct", 0),
                            total = o.optInt("total", 0),
                        )
                    )
                }
            }
        } catch (_: Throwable) {
        }
    }

    private fun ratio(r: GuessRecord): Float =
        if (r.total <= 0) 0f else r.correct.toFloat() / r.total

    /** a 的成绩是否优于 b（先比答对题数，再比正确率） */
    private fun better(a: GuessRecord, b: GuessRecord): Boolean =
        a.correct > b.correct || (a.correct == b.correct && ratio(a) > ratio(b))

    /**
     * 记录一轮成绩（最新在前），返回该轮在历史中的名次（从 1 开始）。
     */
    @Synchronized
    fun add(c: Context, correct: Int, total: Int): Int {
        load(c)
        val r = GuessRecord(System.currentTimeMillis(), correct, total)
        records.add(0, r)
        while (records.size > MAX) records.removeAt(records.size - 1)
        persist(c)
        return records.count { it !== r && better(it, r) } + 1
    }

    /** 历史记录（最新在前） */
    @Synchronized
    fun history(): List<GuessRecord> = records.toList()

    /** 按成绩排名的历史记录（最佳在前），用于展示排行榜 */
    @Synchronized
    fun ranked(): List<GuessRecord> = records.sortedWith(
        compareByDescending<GuessRecord> { it.correct }
            .thenByDescending { ratio(it) }
            .thenByDescending { it.time }
    )

    /** 历史最佳成绩 */
    @Synchronized
    fun best(): GuessRecord? = ranked().firstOrNull()

    /** 共玩过多少轮 */
    @Synchronized
    fun totalRounds(): Int = records.size

    @Synchronized
    fun clear(c: Context) {
        load(c)
        records.clear()
        persist(c)
    }

    /** 时间戳格式化，例如 10-12 07:26 */
    fun format(ts: Long): String =
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))

    @Synchronized
    private fun persist(c: Context) {
        try {
            val arr = JSONArray()
            records.forEach { r ->
                arr.put(
                    JSONObject()
                        .put("time", r.time)
                        .put("correct", r.correct)
                        .put("total", r.total)
                )
            }
            file(c).writeText(arr.toString())
        } catch (_: Throwable) {
        }
    }
}
