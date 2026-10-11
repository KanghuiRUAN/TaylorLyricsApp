@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.example.taylorlyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

// ======================= 主页 =======================

/** 主页：搜索（带命中高亮）+ 词网络入口 + 专辑列表 */
@Composable
fun HomeScreen(
    repository: LyricsRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenTrack: (Track, String) -> Unit,
    onOpenWordNet: (String?) -> Unit,
) {
    var query by remember { mutableStateOf("") }

    // 检索放到后台线程，避免在首帧/输入时卡 UI
    val results by produceState(initialValue = emptyList<Hit>(), query, repository) {
        value = if (query.isBlank()) emptyList()
        else withContext(Dispatchers.Default) { repository.search(query) }
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
            Row(
                Modifier.clip(RoundedCornerShape(20.dp)).background(Color.White.copy(alpha = 0.22f))
                    .clickable { onOpenWordNet(null) }.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.AccountTree, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("词网络 · 查看高频词与关联词", color = Color.White, fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(16.dp)
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

        if (query.isNotBlank()) {
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("未找到匹配的歌曲", color = Soft, fontSize = 15.sp)
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = WindowInsets.navigationBars.asPaddingValues()
                ) {
                    item {
                        Text(
                            "搜索结果 ${results.size} 首 · 金色为关键词命中位置",
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

/** 搜索结果行：歌名 + 专辑 + 歌词命中摘要（均带高亮） */
@Composable
private fun HitRow(hit: Hit, query: String, onClick: () -> Unit) {
    val t = hit.track
    Column(
        Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("%02d".format(t.trackNum), color = Soft, fontSize = 13.sp,
                modifier = Modifier.width(30.dp))
            Icon(Icons.Default.MusicNote, null, tint = Teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (hit.inTitle) highlightWith(t.track, matchRanges(t.track, query))
                    else androidx.compose.ui.text.AnnotatedString(t.track),
                    color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(1.dp))
                Text(t.album, color = Soft, fontSize = 12.sp, maxLines = 1)
            }
            Spacer(Modifier.width(8.dp))
            Text(formatDuration(t.durationSec), color = Soft, fontSize = 12.sp)
        }
        val snip = hit.snip
        if (snip != null) {
            Spacer(Modifier.height(6.dp))
            Row(Modifier.padding(start = 39.dp)) {
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
                modifier = Modifier.padding(start = 39.dp)
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
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(Teal.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Album, null, tint = Teal)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(album.name, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text("${album.tracks.size} 首 · ${album.release.take(4)} 年",
                    color = Soft, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun TrackRow(track: Track, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable(onClick = onClick)
        .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Text("%02d".format(track.trackNum), color = Soft, fontSize = 13.sp,
            modifier = Modifier.width(34.dp))
        Icon(Icons.Default.MusicNote, null, tint = Teal, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(track.track, color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(1.dp))
            Text(track.album, color = Soft, fontSize = 12.sp, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
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
    val tracks = remember(albumName) { repository.albumTracks(albumName) }
    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(albumName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                Text("${tracks.size} 首歌曲", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues()
        ) {
            // 闪退修复：同一专辑存在重复的 disc+trackNum+track（豪华版重复收录），
            // 用它当 LazyColumn key 会 "Key was already used" 崩溃，改用行下标。
            itemsIndexed(tracks, key = { index, _ -> index }) { _, t ->
                TrackRow(t, onClick = { onOpenTrack(t, "") })
                HorizontalDivider(color = Color(0x14000000))
            }
        }
    }
}

// ======================= 歌词页 =======================

/**
 * 歌词页：从搜索结果进入时自动带上关键词并滚动到第一处命中；
 * 支持页内查找 + 上一处/下一处跳转；底部提供该歌高频词与关联词入口。
 */
@Composable
fun LyricsScreen(
    repository: LyricsRepository,
    track: Track,
    initialTerm: String,
    onBack: () -> Unit,
    onOpenWord: (String) -> Unit,
) {
    var showSynced by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf(initialTerm) }
    var cursor by remember { mutableStateOf(0) }

    val syncedLines = remember(track.syncedLyrics) { parseSyncedLyrics(track.syncedLyrics) }
    val plainBody = remember(track.plainLyrics) { track.plainLyrics.trim() }
    val hasSynced = track.hasSyncedLyrics && syncedLines.isNotEmpty()
    val body = if (showSynced && hasSynced) syncedLines.joinToString("\n") { it.text } else plainBody

    val lines = remember(body) { splitLines(body) }
    val ranges = remember(body, term) { matchRanges(body, term) }
    val listState = rememberLazyListState()
    val uiScope = rememberCoroutineScope()

    // 关键词变化时回到第一处命中并滚动过去
    LaunchedEffect(term, body) {
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

    Column(Modifier.fillMaxSize().background(Paper)) {
        Row(Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(track.track, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("${track.album} · ${formatDuration(track.durationSec)}",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1)
            }
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

        // 本首歌的词网络
        WordStrip(repository = repository, track = track, onOpenWord = onOpenWord)
    }
}

@Composable
private fun WordStrip(repository: LyricsRepository, track: Track, onOpenWord: (String) -> Unit) {
    val words by produceState(initialValue = emptyList<WordNode>(), repository, track) {
        value = withContext(Dispatchers.Default) { repository.topWordsIn(track, 18) }
    }
    if (words.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().background(Color.White)
            .navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AccountTree, null, tint = Teal, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("这首歌的高频词 · 点击查看词网络", color = Soft, fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            words.forEach { w ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Teal.copy(alpha = 0.10f),
                    modifier = Modifier.clickable { onOpenWord(w.word) },
                ) {
                    Text(
                        "${w.word} · ${w.total}",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp, color = Teal, fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

// ======================= 词网络 =======================

/**
 * 词网络页：
 *  · 无中心词时展示全库高频枢纽词（字号随覆盖歌曲数变化）
 *  · 有中心词时展示它的关联词（共现边）+ 歌词包含该词的歌曲（带高亮摘要）
 */
@Composable
fun WordNetScreen(
    repository: LyricsRepository,
    centerWord: String?,
    onBack: () -> Unit,
    onPickWord: (String) -> Unit,
    onOpenTrack: (Track, String) -> Unit,
) {
    val hubs by produceState(initialValue = emptyList<WordNode>(), repository) {
        value = withContext(Dispatchers.Default) { repository.topWords(60) }
    }
    val related by produceState(initialValue = emptyList<WordNode>(), centerWord, repository) {
        value = if (centerWord.isNullOrBlank()) emptyList()
        else withContext(Dispatchers.Default) { repository.relatedWords(centerWord, 40) }
    }
    val songs by produceState(initialValue = emptyList<Hit>(), centerWord, repository) {
        value = if (centerWord.isNullOrBlank()) emptyList()
        else withContext(Dispatchers.Default) { repository.songsContaining(centerWord, 120) }
    }

    Column(Modifier.fillMaxSize().background(Bg)) {
        Row(Modifier.fillMaxWidth().background(Teal).statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(
                    centerWord?.let { "词网络 · $it" } ?: "词网络",
                    color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1
                )
                Text(
                    centerWord?.let {
                        "关联词 ${related.size} 个 · 歌词命中 ${songs.size} 首" +
                            if (songs.size >= 120) "（已截断）" else ""
                    } ?: "基于 ${repository.trackCount} 首歌词的共现关系",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, maxLines = 1
                )
            }
        }

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = WindowInsets.navigationBars.asPaddingValues().let {
                PaddingValues(top = it.calculateTopPadding(), bottom = it.calculateBottomPadding() + 12.dp)
            }
        ) {
            item {
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("高频枢纽词", color = Ink, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(2.dp))
                        Text("数字 = 出现在多少首歌里；字号越大越常出现。点击任意词查看它的关联词与歌曲。",
                            color = Soft, fontSize = 12.sp, lineHeight = 17.sp)
                        Spacer(Modifier.height(10.dp))
                        if (hubs.isEmpty()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Teal)
                                Spacer(Modifier.width(10.dp))
                                Text("正在建立词索引…", color = Soft, fontSize = 13.sp)
                            }
                        } else {
                            WordCloud(hubs, centerWord, onPickWord)
                        }
                    }
                }
            }

            if (!centerWord.isNullOrBlank()) {
                item {
                    SectionTitle("「$centerWord」的关联词（同一首歌里最常一起出现）")
                }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            .heightIn(max = 240.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    ) {
                        if (related.isEmpty()) {
                            Box(Modifier.fillMaxWidth().height(80.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = Teal)
                            }
                        } else {
                            Column(Modifier.verticalScroll(rememberScrollState()).padding(14.dp)) {
                                related.chunked(3).forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        row.forEach { w ->
                                            Surface(
                                                modifier = Modifier.weight(1f).clickable { onPickWord(w.word) },
                                                shape = RoundedCornerShape(10.dp),
                                                color = if (w.word == centerWord) Teal.copy(alpha = 0.18f)
                                                else Color(0xFFF1F3F5),
                                            ) {
                                                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                                                    Text(w.word, fontSize = 13.sp, color = Ink,
                                                        fontWeight = FontWeight.Medium, maxLines = 1)
                                                    Text("共现 ${w.songs} 首", fontSize = 10.sp, color = Soft)
                                                }
                                            }
                                        }
                                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                    }
                }

                item { SectionTitle("歌词包含「$centerWord」的歌曲（关键词已高亮）") }
                items(songs) { hit ->
                    HitRow(hit = hit, query = centerWord, onClick = { onOpenTrack(hit.track, centerWord) })
                    HorizontalDivider(color = Color(0x10000000))
                }
                if (songs.isEmpty() && related.isNotEmpty()) {
                    item {
                        Text("该词只出现在少量歌词中，或为变体拼写。",
                            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            color = Soft, fontSize = 12.sp)
                    }
                }
            } else {
                item {
                    Text(
                        "提示：以上面的枢纽词为起点，逐词跳转即可在整张词网里游走。",
                        Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                        color = Soft, fontSize = 12.sp, lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp),
        color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold
    )
}

/** 词云：字号按 log(覆盖歌曲数) 缩放 */
@Composable
private fun WordCloud(
    nodes: List<WordNode>,
    selected: String?,
    onPick: (String) -> Unit,
) {
    val maxSongs = (nodes.maxOfOrNull { it.songs } ?: 1).coerceAtLeast(2)
    val maxLog = ln(maxSongs.toFloat() + 1f)
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
    ) {
        nodes.forEach { n ->
            val ratio = (ln((n.songs + 1).toFloat()) / maxLog).coerceIn(0f, 1f)
            val size = 12f + 12f * ratio
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = if (n.word == selected) Teal else Teal.copy(alpha = 0.06f + 0.16f * ratio),
                modifier = Modifier.clickable { onPick(n.word) },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        n.word,
                        fontSize = size.sp,
                        color = if (n.word == selected) Color.White else Ink,
                        fontWeight = if (ratio > 0.6f) FontWeight.Bold else FontWeight.Normal,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${n.songs}",
                        fontSize = 10.sp,
                        color = if (n.word == selected) Color.White.copy(alpha = 0.85f) else Soft,
                    )
                }
            }
        }
    }
}