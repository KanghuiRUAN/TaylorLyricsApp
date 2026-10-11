package com.example.taylorlyrics

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalAppContext provides applicationContext) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    LyricsApp()
                }
            }
        }
    }
}

/** 导航目标 */
private sealed class Screen {
    object Home : Screen()
    data class Album(val name: String) : Screen()
    data class Lyrics(val track: Track) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsApp() {
    val repository = remember { LyricsRepository.get(LocalAppContext()) }
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Home) }
    var backStack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }

    fun navigate(s: Screen) {
        backStack = backStack + s
        currentScreen = s
    }
    fun back() {
        if (backStack.size > 1) {
            backStack = backStack.dropLast(1)
            currentScreen = backStack.last()
        }
    }

    when (val s = currentScreen) {
        is Screen.Home -> HomeScreen(repository, onOpenAlbum = { navigate(Screen.Album(it)) }, onOpenTrack = { navigate(Screen.Lyrics(it)) })
        is Screen.Album -> AlbumScreen(repository, s.name, onBack = ::back, onOpenTrack = { navigate(Screen.Lyrics(it)) })
        is Screen.Lyrics -> LyricsScreen(s.track, onBack = ::back)
    }
}

// ---------------- Local helpers ----------------
val LocalAppContext = staticCompositionLocalOf<android.content.Context> {
    error("No context provided")
}