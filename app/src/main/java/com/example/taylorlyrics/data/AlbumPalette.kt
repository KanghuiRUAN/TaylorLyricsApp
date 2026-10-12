package com.example.taylorlyrics.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache

/**
 * 专辑封面索引 + 时代配色。
 *  · 封面图片打包在 assets/cover_XX.jpg（与 cleanup 规则里的专辑显示名一一对应）；
 *  · 每张专辑配一个"时代主色"（取自该专辑封面/视觉的氛围色），
 *    用于顶部栏、收藏色块等点缀，让浏览时有"时代切换"的感觉；
 *  · 没有封面的专辑回退为时代色块 + 首字母。
 */
object AlbumPalette {

    /** 专辑显示名 -> assets 资源名（cover_XX.jpg） */
    private val ART: Map<String, String> = mapOf(
        "Taylor Swift" to "cover_00",
        "Fearless" to "cover_01",
        "Speak Now" to "cover_02",
        "Red" to "cover_03",
        "1989" to "cover_04",
        "reputation" to "cover_05",
        "Lover" to "cover_06",
        "folklore" to "cover_07",
        "evermore" to "cover_08",
        "Midnights" to "cover_09",
        "THE TORTURED POETS DEPARTMENT" to "cover_10",
        "The Life of a Showgirl" to "cover_11",
        "Fearless (Taylor's Version)" to "cover_12",
        "Speak Now (Taylor's Version)" to "cover_13",
        "Red (Taylor's Version)" to "cover_14",
        "1989 (Taylor's Version)" to "cover_15",
        "The Taylor Swift Holiday Collection" to "cover_16",
        "Safe & Sound (feat. The Civil Wars)" to "cover_17",
        "Safe & Sound (Taylor's Version)" to "cover_18",
        "Ronan" to "cover_19",
        "Sweeter Than Fiction" to "cover_20",
        "Bad Blood (feat. Kendrick Lamar)" to "cover_21",
        "Only The Young" to "cover_22",
        "Today Was a Fairytale" to "cover_23",
        "Christmas Tree Farm" to "cover_24",
        "Carolina" to "cover_25",
        "All Of The Girls You Loved Before" to "cover_26",
        "You're Losing Me (From The Vault)" to "cover_27",
        "Wildest Dreams (Taylor's Version)" to "cover_28",
        "The Lakes" to "cover_29",
        "The Joker And The Queen (feat. Taylor Swift)" to "cover_30",
        "Opalite" to "cover_31",
        "Elizabeth Taylor" to "cover_32",
        "I Knew It, I Knew You (From \"Toy Story 5\")" to "cover_33",
        "The Fate of Ophelia" to "cover_34",
        "All Too Well (10 Minute Version)" to "cover_35",
        "Message In A Bottle (Taylor's Version)" to "cover_36",
        "This Love (Taylor's Version)" to "cover_37",
        "Renegade (Pop Version)" to "cover_38",
        "Cardigan (Cabin In Candlelight Version)" to "cover_39",
    )

    /** 时代主色（ARGB int） */
    private val TINT: Map<String, Long> = mapOf(
        "Taylor Swift" to 0xFF5B7E95,
        "Fearless" to 0xFFB08D3E,
        "Speak Now" to 0xFF7D5BA6,
        "Red" to 0xFFC43B41,
        "1989" to 0xFF4FA8C7,
        "reputation" to 0xFF3C3C3C,
        "Lover" to 0xFFE58BB1,
        "folklore" to 0xFF6E7B74,
        "evermore" to 0xFFB4602F,
        "Midnights" to 0xFF2E4372,
        "THE TORTURED POETS DEPARTMENT" to 0xFF8C7B6B,
        "The Life of a Showgirl" to 0xFFE0653A,
        "Fearless (Taylor's Version)" to 0xFFC9A227,
        "Speak Now (Taylor's Version)" to 0xFF8E6BB5,
        "Red (Taylor's Version)" to 0xFFB02A30,
        "1989 (Taylor's Version)" to 0xFF55808F,
        "The Taylor Swift Holiday Collection" to 0xFFB33A3A,
        "Safe & Sound (feat. The Civil Wars)" to 0xFF6F8577,
        "Safe & Sound (Taylor's Version)" to 0xFF7C8F83,
        "Ronan" to 0xFF7E93A3,
        "Sweeter Than Fiction" to 0xFFD8A62E,
        "Bad Blood (feat. Kendrick Lamar)" to 0xFF8E2F3C,
        "Only The Young" to 0xFF3E6FA8,
        "Today Was a Fairytale" to 0xFF8A7FA8,
        "Christmas Tree Farm" to 0xFF3E7D4F,
        "Carolina" to 0xFF5E6B54,
        "All Of The Girls You Loved Before" to 0xFFE39BB4,
        "You're Losing Me (From The Vault)" to 0xFF6E7B8C,
        "Wildest Dreams (Taylor's Version)" to 0xFF9C7BB0,
        "The Lakes" to 0xFF708273,
        "The Joker And The Queen (feat. Taylor Swift)" to 0xFF5A5A5A,
        "Opalite" to 0xFFE7A23C,
        "Elizabeth Taylor" to 0xFF8C5FA8,
        "I Knew It, I Knew You (From \"Toy Story 5\")" to 0xFF4A90D9,
        "The Fate of Ophelia" to 0xFF3E8E8A,
        "All Too Well (10 Minute Version)" to 0xFFA32E2E,
        "Message In A Bottle (Taylor's Version)" to 0xFF6A5FC7,
        "This Love (Taylor's Version)" to 0xFF7FA8C9,
        "Renegade (Pop Version)" to 0xFFD97B3E,
        "Cardigan (Cabin In Candlelight Version)" to 0xFF7D8A80,
    )

    private val DEFAULT_TINT: Int = 0xFF1DB0A6.toInt()

    fun artKey(album: String): String? = ART[album]

    /** 专辑时代色；未收录的专辑回退到应用主题色 */
    fun tintOf(album: String): Int {
        val hex: Long? = TINT[album]
        if (hex == null) return DEFAULT_TINT
        return (hex and 0xFFFFFFFFL).toInt()
    }

    // ---- 封面解码（带内存缓存，列表滚动时避免重复解码） ----

    private val cache = LruCache<String, Bitmap>(40)

    fun decode(ctx: Context, album: String): Bitmap? {
        val key = artKey(album) ?: return null
        cache.get(key)?.let { return it }
        return try {
            ctx.assets.open("$key.jpg").use { BitmapFactory.decodeStream(it) }
        } catch (_: Throwable) {
            null
        }?.also { cache.put(key, it) }
    }
}