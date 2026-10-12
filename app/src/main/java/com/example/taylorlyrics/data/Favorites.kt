package com.example.taylorlyrics.data

import android.content.Context
import org.json.JSONArray
import java.io.File

/**
 * 收藏：轻量本地存储，直接读写 filesDir 下的 JSON 文件。
 * 不引入 DataStore/Room，保持零依赖；key 由 专辑+disc+trackNum+曲名 组成，
 * 重名/重号曲目也不会互相覆盖。
 */
object Favorites {
    private const val FILE = "favorites.json"
    private val keys = LinkedHashSet<String>()
    private var loaded = false

    @Synchronized
    private fun file(c: Context) = File(c.filesDir, FILE)

    @Synchronized
    fun key(t: Track): String = "${t.album}\u0001${t.disc}\u0001${t.trackNum}\u0001${t.track}"

    @Synchronized
    fun load(c: Context) {
        if (loaded) return
        loaded = true
        try {
            val f = file(c)
            if (f.exists()) {
                val arr = JSONArray(f.readText())
                for (i in 0 until arr.length()) keys.add(arr.getString(i))
            }
        } catch (_: Throwable) {
        }
    }

    @Synchronized
    fun all(): List<String> = keys.toList()

    @Synchronized
    fun isFav(c: Context, t: Track): Boolean {
        load(c)
        return keys.contains(key(t))
    }

    /** 切换收藏状态，返回切换后是否已收藏 */
    @Synchronized
    fun toggle(c: Context, t: Track): Boolean {
        load(c)
        val k = key(t)
        val added = if (keys.contains(k)) {
            keys.remove(k); false
        } else {
            keys.add(k); true
        }
        persist(c)
        return added
    }

    @Synchronized
    private fun persist(c: Context) {
        try {
            val arr = JSONArray()
            keys.forEach { arr.put(it) }
            file(c).writeText(arr.toString())
        } catch (_: Throwable) {
        }
    }
}