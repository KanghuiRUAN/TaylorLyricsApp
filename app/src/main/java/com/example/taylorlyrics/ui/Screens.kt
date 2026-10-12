@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalFoundationApi::class)

package com.example.taylorlyrics.ui

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taylorlyrics.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.ln

private val Teal = Color(0xFF1DB0A6)
private val Ink = Color(0xFF202124)
private val Soft = Color(0x993C4043)
private val Bg = Color(0xFFF7F8FA)
private val Paper = Color(0xFFFBFBF6)
private val Gold = Color(0xFFE8A33D)

/** 时代主色：专辑专属点缀色（没有收录的专辑回退应用主题色） */
private fun eraAccent(album: String): Color = Color(AlbumPalette.tintOf(album))

// ======================= 通用小组件 =======================

/** 专辑封面：解码打包在 assets 里的图片；缺失时回退为时代色块 + 首字母 */
@Composable
private fun AlbumArt(album: String, size: Dp, radius: Dp = 10.dp, fallbackText: TextUnit = 16.sp) {
    val ctx = LocalContext.current
    val bmp = remember(album) { AlbumPalette.decode(ctx, album) }
    Box(Modifier.size(size).clip(RoundedCornerShape(radius))) {
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = album,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(eraAccent(album)), contentAlignment = Alignment.Center) {
                Text(album.take(1).uppercase(), color = Color.White, fontSize = fallbackText, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** 收藏星标 */
@Composable
private fun FavStar(
    isFav: Boolean,
    onToggle: () -> Unit,
    size: Dp = 24.dp,
    tintOn: Color = Gold,
    tintOff: Color = Soft,
) {
    Icon(
        if (isFav) Icons.Default.Star else Icons.Default.StarBorder,
        contentDescription = if (isFav) "取消收藏" else "收藏",
        tint = if (isFav) tintOn else tintOff,
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .padding(2.dp),
    )
}

/** 首页顶部入口胶囊 */
@Composable
private fun HeaderChip(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.22f))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** 小操作按钮（复制 / 分享等） */
@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = Color(0xFFF1F3F5),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            fontSize = 12.sp, color = Ink, fontWeight = FontWeight.Medium,
        )
    }
}

/** 词边界匹配：只保留"整词"命中（glove 里的 love 不会算数） */
private fun wholeWordRanges(body: String, term: String): List<IntRange> {
    val q = normalize(term.trim())
    if (q.isEmpty()) return emptyList()
    val n = normOf(body)
    return normRanges(n, q).filter { r ->
        val before = r.first == 0 || !n.text[r.first - 1].isLetterOrDigit()
        val after = r.last + 1 >= n.text.length || !n.text[r.last + 1].isLetterOrDigit()
        before && after
    }
}

// ======================= 主页 =======================

/**
 * 主页：搜索（整词 / 短语两种模式，带命中高亮）+ 随机惊喜 + 收藏 + 专辑列表（带封面）
 */
@Composable
fun HomeScreen(
    repository: LyricsRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenTrack: (Track, String) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenGuess: () -> Unit,
    onSurprise: () -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var phraseMode by remember { mutableStateOf(false) }

    // 检索放到后台线程，避免在首帧/输入时卡 UI
    val results by produceState(initialValue = emptyList<Hit>(), query, phraseMode, repository) {
        value = if (query.isBlank()) emptyList()
        else withContext(Dispatchers.Default) {
            if (phraseMode) repository.searchPhrase(query) else repository.search(query)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Column(
            Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 18.dp)
        ) {
            Text("Taylor Swift 歌词库", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${repository.trackCount} 首歌曲 · ${repository.albumCount} 张专辑 · 离线全量",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp
            )
            Spacer(Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                HeaderChip(Icons.Default.Style, "随便来一首", onClick = onSurprise)
                HeaderChip(Icons.Default.StarBorder, "我的收藏", onClick = onOpenFavorites)
                HeaderChip(Icons.Default.SmartToy, "猜歌词", onClick = onOpenGuess)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(28.dp)).background(Color.White)
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, null, tint = Soft)
            Spacer(Modifier.width(8.dp))
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("搜索歌名 / 歌词…", color = Soft, fontSize = 14.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
            )
            if (query.isNotEmpty()) {
                Icon(Icons.Default.Close, null, tint = Soft,
                    modifier = Modifier.clickable { query = "" })
            }
        }

        // 搜索模式：整词（默认，带词边界过滤）/ 短语（按词序原样连续匹配）
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = !phraseMode, onClick = { phraseMode = false },
                label = { Text("整词", fontSize = 12.sp) },
                modifier = Modifier.height(30.dp),
            )
            Spacer(Modifier.width(6.dp))
            FilterChip(
                selected = phraseMode, onClick = { phraseMode = true },
                label = { Text("短语", fontSize = 12.sp) },
                modifier = Modifier.height(30.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (phraseMode) "按词序逐词连续匹配（标点/空格不影响）"
                else "歌名优先 + 歌词整词匹配",
                color = Soft, fontSize = 11.sp
            )
        }

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text(
                        if (phraseMode) "没有整句命中（可以切回整词模式再试试）"
                        else "未找到匹配的歌曲",
                        color = Soft, fontSize = 15.sp
                    )
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                ) {
                    item {
                        Text(
                            if (phraseMode) "短语命中 ${results.size} 首 · 金色为原句命中位置"
                            else "搜索结果 ${results.size} 首 · 金色为关键词命中位置",
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = Soft, fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    // 不传 key：整体重建，规避重复曲目导致的 duplicate-key 崩溃
                    items(results) { hit ->
                        HitRow(hit = hit, query = query, onClick = { onOpenTrack(hit.track, query) })
                        HorizontalDivider(color = Color(0x10000000))
                    }
                }
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                items(repository.allAlbums()) { album ->
                    AlbumRow(album, onClick = { onOpenAlbum(album.name) })
                }
            }
        }
    }
}

/** 搜索结果行：封面 + 歌名 + 专辑 + 歌词命中摘要（均带高亮） */
@Composable
private fun HitRow(hit: Hit, query: String, onClick: () -> Unit) {
    val t = hit.track
    Column(
        Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(t.album, 40.dp, 8.dp, 14.sp)
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hit.inTitle) highlightWith(t.track, matchRanges(t.track, query))
                    else AnnotatedString(t.track),
                    color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(1.dp))
                Text(t.album, color = Soft, fontSize = 12.sp, maxLines = 1)
            }
            if (t.streams > 0L) {
                Spacer(Modifier.width(8.dp))
                Text("🔥${formatStreams(t.streams)}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(t.durationSec), color = Soft, fontSize = 12.sp)
        }
        val snip = hit.snip
        if (snip != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(start = 51.dp)) {
                Text(
                    if (hit.inTitle) "歌词也命中" else "歌词命中",
                    color = MarkColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(end = 6.dp, top = 1.dp)
                )
                Text(
                    highlightWith(snip.text, snip.ranges),
                    color = Soft, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 2,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                "共 ${snip.total} 处命中" + if (snip.truncated) "（点击进入歌词页查看全部）" else "",
                color = MarkColor.copy(alpha = 0.75f), fontSize = 11.sp,
                modifier = Modifier.padding(start = 51.dp)
            )
        }
    }
}

@Composable
private fun AlbumRow(album: AlbumInfo, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            AlbumArt(album.name, 56.dp, 10.dp, 18.sp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("${album.tracks.size} 首 · ${album.release.take(4)} 年",
                    color = Soft, fontSize = 12.sp)
                val hot = album.tracks.maxOfOrNull { it.streams } ?: 0L
                if (hot > 0L) {
                    Spacer(Modifier.height(1.dp))
                    Text("最热 🔥${formatStreams(hot)}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isFav: Boolean = false,
    onToggleFav: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(track.trackNum), color = Soft, fontSize = 13.sp,
            modifier = Modifier.width(34.dp))
        AlbumArt(track.album, 44.dp, 9.dp, 15.sp)
        Spacer(Modifier.width(11.dp))
        Column(Modifier.weight(1f)) {
            Text(track.track, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(1.dp))
            Text(track.album, color = Soft, fontSize = 12.sp, maxLines = 1)
        }
        if (onToggleFav != null) {
            FavStar(isFav = isFav, onToggle = onToggleFav)
            Spacer(Modifier.width(6.dp))
        }
        if (track.streams > 0L) {
            Text("🔥${formatStreams(track.streams)}", color = Teal, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.width(6.dp))
        }
        Text(formatDuration(track.durationSec), color = Soft, fontSize = 12.sp)
    }
}

// ======================= 专辑详情 =======================

@Composable
fun AlbumScreen(
    repository: LyricsRepository,
    albumName: String,
    onBack: () -> Unit,
    onOpenTrack: (Track, String) -> Unit,
) {
    val ctx = LocalContext.current
    val accent = eraAccent(albumName)
    val tracks = remember(albumName) { repository.albumTracks(albumName) }
    var fav by remember(albumName) { mutableStateOf(false) }
    LaunchedEffect(albumName) {
        fav = tracks.firstOrNull()?.let { Favorites.isFav(ctx, it) } ?: false
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        // 顶部栏使用专辑时代色
        Row(Modifier.fillMaxWidth().background(accent).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(albumName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${tracks.size} 首歌曲", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
            FavStar(
                isFav = fav,
                onToggle = {
                    val added = repository.toggleAlbumFav(ctx, albumName)
                    fav = added
                    Toast.makeText(ctx, if (added) "已收藏整张专辑" else "已取消收藏", Toast.LENGTH_SHORT).show()
                },
                tintOff = Color.White.copy(alpha = 0.85f),
            )
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    AlbumArt(albumName, 92.dp, 12.dp, 26.sp)
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(albumName, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(3.dp))
                        Text("发行于 ${tracks.firstOrNull()?.albumRelease?.take(4).orEmpty()} 年",
                            color = Soft, fontSize = 13.sp)
                        Spacer(Modifier.height(3.dp))
                        Text("右上角 ☆ 可整张收藏", color = Soft, fontSize = 11.sp)
                    }
                }
            }
            // 闪退修复：同一专辑存在重复的 disc+trackNum+track（豪华版重复收录），
            // 用它当 LazyColumn key 会 "Key was already used" 崩溃，改用行下标。
            itemsIndexed(tracks, key = { index, _ -> index }) { _, t ->
                var rfav by remember { mutableStateOf(false) }
                LaunchedEffect(t) { rfav = Favorites.isFav(ctx, t) }
                TrackRow(
                    track = t,
                    isFav = rfav,
                    onToggleFav = { rfav = Favorites.toggle(ctx, t) },
                    onClick = { onOpenTrack(t, "") },
                )
                HorizontalDivider(color = Color(0x14000000))
            }
        }
    }
}

// ======================= 歌词页 =======================

/**
 * 歌词页：
 *  · 顶部栏使用专辑时代色 + 封面缩略 + 收藏星标
 *  · 页内查找支持 整词 / 短语 两种匹配模式
 *  · 空行分段的段落胶囊：点击跳段（Bridge 等靠后的段在列表后部）、长按复制整段
 *  · 支持复制 / 分享整首歌词
 */
@Composable
fun LyricsScreen(
    repository: LyricsRepository,
    track: Track,
    initialTerm: String,
    onBack: () -> Unit,
    onToggleFav: () -> Unit,
) {
    val ctx = LocalContext.current
    val clip = LocalClipboardManager.current
    val accent = eraAccent(track.album)

    var showSynced by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf(initialTerm) }
    // 带空格的初始词（从短语搜索点进来）自动切到短语模式
    var phraseOnly by remember { mutableStateOf(initialTerm.contains(' ')) }
    var cursor by remember { mutableStateOf(0) }
    var fav by remember { mutableStateOf(false) }

    val syncedLines = remember(track.syncedLyrics) { parseSyncedLyrics(track.syncedLyrics) }
    val plainBody = remember(track.plainLyrics) { track.plainLyrics.trim() }
    val hasSynced = track.hasSyncedLyrics && syncedLines.isNotEmpty()
    val body = if (showSynced && hasSynced) syncedLines.joinToString("\n") { it.text } else plainBody

    val lines = remember(body) { splitLines(body) }
    val sections = remember(lines) { splitSections(lines) }

    // 匹配区间：短语模式按词序原样匹配；整词模式带词边界过滤
    val ranges = remember(body, term, phraseOnly) {
        if (term.isBlank()) emptyList()
        else if (phraseOnly) matchRanges(body, term)
        else wholeWordRanges(body, term)
    }
    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()

    LaunchedEffect(track) { fav = Favorites.isFav(ctx, track) }

    // 关键词变化时回到第一处命中并滚动过去
    LaunchedEffect(term, phraseOnly, body) {
        cursor = 0
        if (ranges.isNotEmpty()) {
            val li = lines.indexOfFirst { term.isNotBlank() && ranges.first().first < it.start + it.text.length }
            if (li >= 0) listState.animateScrollToItem(li, scrollOffset = -40)
        }
    }

    fun jumpTo(index: Int) {
        if (ranges.isEmpty()) return
        val i = ((index % ranges.size) + ranges.size) % ranges.size
        cursor = i
        val r = ranges[i]
        val li = lines.indexOfFirst { r.first < it.start + it.text.length }
        if (li >= 0) {
            uiScope.launch { listState.animateScrollToItem(li.coerceAtLeast(0), scrollOffset = -60) }
        }
    }

    fun copyText(text: String, toast: String) {
        clip.setText(AnnotatedString(text))
        Toast.makeText(ctx, toast, Toast.LENGTH_SHORT).show()
    }

    fun shareText(text: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "${track.track} - ${track.album}")
            putExtra(Intent.EXTRA_TEXT, text)
        }
        ctx.startActivity(Intent.createChooser(intent, "分享歌词"))
    }

    /** 取一个段落的完整文本（到下一段开头为止） */
    fun sectionText(sec: Section): String {
        val end = sections.getOrNull(sec.index + 1)?.firstLine ?: lines.size
        return lines.subList(sec.firstLine, end).joinToString("\n") { it.text }.trim()
    }

    Column(Modifier.fillMaxSize().background(Paper)) {
        // 顶部栏：时代色 + 封面 + 收藏
        Row(Modifier.fillMaxWidth().background(accent).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            AlbumArt(track.album, 38.dp, 8.dp, 13.sp)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(track.track, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.album, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            }
            FavStar(
                isFav = fav, onToggle = onToggleFav,
                tintOff = Color.White.copy(alpha = 0.85f),
            )
            if (hasSynced) {
                Row(Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.22f))
                    .padding(3.dp)) {
                    FilterChip(selected = !showSynced, onClick = { showSynced = false },
                        label = { Text("歌词", fontSize = 12.sp, color = Color.White) },
                        modifier = Modifier.height(30.dp))
                    Spacer(Modifier.width(2.dp))
                    FilterChip(selected = showSynced, onClick = { showSynced = true },
                        label = { Text("逐行", fontSize = 12.sp, color = Color.White) },
                        modifier = Modifier.height(30.dp))
                }
            }
        }

        // 页内查找条
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color.White).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Soft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            TextField(
                value = term,
                onValueChange = { term = it },
                placeholder = { Text("在此歌词中查找…", color = Soft, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                )
            )
            if (term.isNotEmpty()) {
                Text(
                    if (ranges.isEmpty()) "无匹配" else "${(cursor % ranges.size) + 1}/${ranges.size}",
                    color = if (ranges.isEmpty()) Color(0xFFC0392B) else MarkColor,
                    fontSize = 12.sp, fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.width(4.dp))
                TextButton(onClick = { jumpTo(cursor - 1) }, enabled = ranges.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↑", color = if (ranges.isNotEmpty()) Teal else Soft, fontSize = 15.sp)
                }
                TextButton(onClick = { jumpTo(cursor + 1) }, enabled = ranges.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 6.dp)) {
                    Text("↓", color = if (ranges.isNotEmpty()) Teal else Soft, fontSize = 15.sp)
                }
                Icon(Icons.Default.Close, null, tint = Soft,
                    modifier = Modifier.clickable { term = "" }.size(18.dp))
            }
        }

        // 整词 / 短语切换（有搜索词时显示）
        if (term.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = !phraseOnly, onClick = { phraseOnly = false },
                    label = { Text("整词", fontSize = 11.sp) }, modifier = Modifier.height(26.dp))
                Spacer(Modifier.width(5.dp))
                FilterChip(selected = phraseOnly, onClick = { phraseOnly = true },
                    label = { Text("短语", fontSize = 11.sp) }, modifier = Modifier.height(26.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    if (phraseOnly) "按词序逐词连续匹配（标点/空格不影响）"
                    else "只匹配完整单词（glove 里的 love 不会算数）",
                    color = Soft, fontSize = 11.sp, maxLines = 1
                )
            }
        }

        // 复制 / 分享
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MiniButton("复制歌词") { copyText(body, "歌词已复制") }
            MiniButton("分享歌词") { shareText("${track.track} - ${track.album}\n\n$body") }
        }

        // 热度 & 场景标签（v1.4）
        if (track.streams > 0L || track.tags.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (track.streams > 0L) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Teal.copy(alpha = 0.10f),
                    ) {
                        Text(
                            "🔥 " + formatStreams(track.streams),
                            Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            fontSize = 12.sp, color = Teal, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (track.tags.isNotEmpty()) {
                    Spacer(Modifier.width(6.dp))
                    Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        track.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = accent.copy(alpha = 0.12f),
                            ) {
                                Text(
                                    "#$tag",
                                    Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                                    fontSize = 11.sp, color = accent, fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }

        // 段落跳转：数据源没有 [Bridge] 标记，按空行分段；Bridge / Outro 通常是靠后的段
        if (sections.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                sections.forEach { sec ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = accent.copy(alpha = 0.12f),
                        modifier = Modifier.combinedClickable(
                            onClick = {
                                uiScope.launch {
                                    listState.animateScrollToItem(sec.firstLine, scrollOffset = -40)
                                }
                            },
                            onLongClick = { copyText(sectionText(sec), "已复制${sec.title}") },
                        ),
                    ) {
                        Text(
                            "${sec.title} · ${sec.preview}",
                            Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            fontSize = 12.sp, color = accent, fontWeight = FontWeight.Medium, maxLines = 1,
                        )
                    }
                }
            }
        }

        if (body.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("此曲目暂无歌词（可能为伴奏 / 卡拉OK版）", color = Soft, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 22.dp, end = 22.dp, top = 6.dp, bottom = 24.dp),
            ) {
                itemsIndexed(lines, key = { i, _ -> i }) { _, line ->
                    val local = clipRanges(ranges, line.start, line.start + line.text.length)
                    Text(
                        highlightWith(line.text, local),
                        color = if (line.text.isBlank()) Soft else Ink,
                        fontSize = 15.sp,
                        lineHeight = 26.sp,
                        fontFamily = FontFamily.SansSerif,
                    )
                }
            }
        }
    }
}

// ======================= 收藏页 =======================

@Composable
fun FavoritesScreen(
    repository: LyricsRepository,
    onBack: () -> Unit,
    onOpenTrack: (Track, String) -> Unit,
) {
    val ctx = LocalContext.current
    val favTracks by produceState(initialValue = emptyList<Track>(), repository) {
        value = withContext(Dispatchers.Default) {
            Favorites.load(ctx)
            val ks = Favorites.all()
            if (ks.isEmpty()) emptyList() else repository.tracksByKeys(ks)
        }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("我的收藏", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${favTracks.size} 首 · 保存在本机", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }
        if (favTracks.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "还没有收藏\n\n在歌曲或专辑页点亮 ☆ 即可收藏",
                    color = Soft, fontSize = 14.sp, lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                itemsIndexed(favTracks, key = { i, _ -> i }) { _, t ->
                    var rfav by remember { mutableStateOf(true) }
                    TrackRow(
                        track = t,
                        isFav = rfav,
                        onToggleFav = { rfav = Favorites.toggle(ctx, t) },
                        onClick = { onOpenTrack(t, "") },
                    )
                    HorizontalDivider(color = Color(0x14000000))
                }
            }
        }
    }
}

// ======================= 猜歌词小游戏 =======================

@Composable
fun GuessScreen(
    repository: LyricsRepository,
    onBack: () -> Unit,
    onOpenTrack: (Track, String) -> Unit,
) {
    var gameId by remember { mutableStateOf(0) }
    // 出题：随机抽 10 首有歌词的歌
    val rounds = remember(gameId) {
        repository.sampleTracks(40).filter { it.plainLyrics.isNotBlank() }.take(10)
    }
    var idx by remember(gameId) { mutableStateOf(0) }
    var correct by remember(gameId) { mutableStateOf(0) }
    var answered by remember(gameId) { mutableStateOf(false) }
    var picked by remember(gameId) { mutableStateOf(-1) }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text("猜歌词", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("这句词出自哪首歌？", color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
        }

        when {
            rounds.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("曲库为空", color = Soft, fontSize = 14.sp)
                }
            }
            idx >= rounds.size -> {
                // 结果页
                Column(
                    Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("🎉", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("答对 ${correct} / ${rounds.size}",
                        color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        when {
                            correct == rounds.size -> "全对！官方认证 Swiftie！"
                            correct >= rounds.size * 3 / 4 -> "好近了，you belong with me."
                            correct >= rounds.size / 2 -> "Fearless 水准，继续保持！"
                            else -> "没关系，曲库一直在这里等你。"
                        },
                        color = Soft, fontSize = 14.sp
                    )
                    Spacer(Modifier.height(20.dp))
                    Button(onClick = { gameId++ }) { Text("再玩一轮") }
                }
            }
            else -> {
                val current = rounds[idx]
                val lyricLine = remember(current) {
                    val ls = current.plainLyrics.trim().split('\n').map { it.trim() }.filter { it.isNotBlank() }
                    if (ls.isEmpty()) "" else if (ls.size > 2) ls[1] else ls.first()
                }
                val options = remember(current) {
                    val wrongs = repository.sampleTracks(40)
                        .map { it.track }
                        .distinct()
                        .filter { it != current.track }
                        .take(3)
                    (wrongs + current.track).shuffled()
                }

                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                    Text("第 ${idx + 1} 题 / ${rounds.size} · 已答对 $correct",
                        color = Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(14.dp))
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Paper),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        Column(Modifier.fillMaxWidth().padding(20.dp)) {
                            Text("“", color = Teal, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                            Text(lyricLine, color = Ink, fontSize = 18.sp, lineHeight = 27.sp,
                                fontFamily = FontFamily.Serif)
                            Spacer(Modifier.height(6.dp))
                            Text("—— 这句词出自哪首歌？", color = Soft, fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    options.forEachIndexed { oi, opt ->
                        val isRight = opt == current.track
                        val bg = when {
                            !answered -> Color.White
                            isRight -> Color(0xFFDFF2E4)
                            oi == picked -> Color(0xFFFCE4E2)
                            else -> Color.White
                        }
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = bg,
                            border = if (!answered) BorderStroke(1.dp, Color(0x14000000)) else null,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                .clickable(enabled = !answered) {
                                    picked = oi
                                    answered = true
                                    if (isRight) correct++
                                },
                        ) {
                            Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text("%c".format('A' + oi), color = Soft, fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(10.dp))
                                Text(opt, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                                    modifier = Modifier.weight(1f))
                                if (answered && isRight) {
                                    Text("✓", color = Color(0xFF2E9E5B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                if (answered && !isRight && oi == picked) {
                                    Text("✗", color = Color(0xFFC0392B), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    if (answered) {
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { idx++; answered = false; picked = -1 }) {
                                Text(
                                    if (idx + 1 >= rounds.size) "看结果 →" else "下一题 →",
                                    color = Teal, fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                        TextButton(onClick = { onOpenTrack(current, "") }) {
                            Text("打开这首歌词复习", color = Soft, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}