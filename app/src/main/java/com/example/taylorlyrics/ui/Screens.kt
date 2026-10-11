@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.taylorlyrics.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.taylorlyrics.data.*

private val Teal = Color(0xFF1DB0A6)
private val Ink = Color(0xFF202124)
private val Soft = Color(0x993C4043)

/** 主页：搜索 + 专辑列表 */
@Composable
fun HomeScreen(
    repository: LyricsRepository,
    onOpenAlbum: (String) -> Unit,
    onOpenTrack: (Track) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { if (query.isBlank()) emptyList() else repository.search(query) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
        // header
        Column(Modifier.fillMaxWidth().background(Teal).padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text("Taylor Swift 歌词库", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("${repository.trackCount} 首歌曲 · ${repository.albumCount} 张专辑 · 离线全量",
                color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp)
        }
        // search bar
        Row(Modifier.fillMaxWidth().padding(16.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.White)
            .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
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
            // search results
            if (results.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    Text("未找到匹配的歌曲", color = Soft, fontSize = 15.sp)
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("搜索结果 (${results.size})", Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                            color = Soft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                    items(results) { t -> TrackRow(t, onClick = { onOpenTrack(t) }) }
                }
            }
        } else {
            // album list
            LazyColumn(Modifier.fillMaxSize()) {
                items(repository.allAlbums(), key = { it.name }) { album ->
                    AlbumRow(album, onClick = { onOpenAlbum(album.name) })
                }
            }
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

/** 专辑详情：曲目列表 */
@Composable
fun AlbumScreen(
    repository: LyricsRepository,
    albumName: String,
    onBack: () -> Unit,
    onOpenTrack: (Track) -> Unit,
) {
    val tracks = remember(albumName) { repository.albumTracks(albumName) }
    Column(Modifier.fillMaxSize().background(Color(0xFFF7F8FA))) {
        Row(Modifier.fillMaxWidth().background(Teal).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column {
                Text(albumName, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold,
                    maxLines = 2)
                Text("${tracks.size} 首歌曲", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
            }
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tracks, key = { "${it.disc}-${it.trackNum}-${it.track}" }) { t ->
                TrackRow(t, onClick = { onOpenTrack(t) })
                HorizontalDivider(color = Color(0x14000000))
            }
        }
    }
}

/** 歌词页 */
@Composable
fun LyricsScreen(track: Track, onBack: () -> Unit) {
    var showSynced by remember { mutableStateOf(false) }
    var searchTerm by remember { mutableStateOf("") }
    val syncedLines = remember(track.syncedLyrics) { parseSyncedLyrics(track.syncedLyrics) }
    val lyricsBody = remember(track.plainLyrics) { track.plainLyrics.trim() }
    val hasSynced = track.hasSyncedLyrics && syncedLines.isNotEmpty()

    Column(Modifier.fillMaxSize().background(Color(0xFFFBFBF6))) {
        // top bar
        Row(Modifier.fillMaxWidth().background(Teal).padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White) }
            Column(Modifier.weight(1f)) {
                Text(track.track, color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                Text("${track.album} · ${formatDuration(track.durationSec)}",
                    color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp)
            }
            if (hasSynced) {
                // toggle plain / synced
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

        // inline search bar (lyrics keyword highlight)
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color.White)
            .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Search, null, tint = Soft, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            TextField(
                value = searchTerm,
                onValueChange = { searchTerm = it },
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
            if (searchTerm.isNotEmpty()) {
                Icon(Icons.Default.Close, null, tint = Soft,
                    modifier = Modifier.clickable { searchTerm = "" }.size(18.dp))
            }
        }

        val body = if (showSynced && hasSynced) {
            syncedLines.joinToString("\n") { it.text }
        } else lyricsBody

        if (body.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
                Text("此曲目暂无歌词（可能为伴奏 / 卡拉OK版）", color = Soft, fontSize = 14.sp)
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 12.dp)) {
                Text(
                    highlight(body, searchTerm),
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 26.sp,
                    fontFamily = FontFamily.SansSerif,
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** 把关键词高亮为金色 */
private fun highlight(text: String, term: String): androidx.compose.ui.text.AnnotatedString {
    val t = term.trim()
    if (t.isEmpty()) return androidx.compose.ui.text.AnnotatedString(text)
    val q = normalize(t)
    val sb = buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            // find a token in text whose normalized form matches
            var found = false
            for (end in i until text.length + 1) {
                if (end - i > 60) break
                val seg = text.substring(i, end)
                if (normalize(seg) == q) {
                    withStyle(SpanStyle(color = Color(0xFFB87333), fontWeight = FontWeight.Bold)) {
                        append(seg)
                    }
                    i = end
                    found = true
                    break
                }
            }
            if (!found) {
                append(text[i])
                i++
            }
        }
    }
    return sb
}