package com.example.taylorlyrics

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.example.taylorlyrics.data.Favorites
import com.example.taylorlyrics.data.LyricsRepository
import com.example.taylorlyrics.data.Track
import com.example.taylorlyrics.ui.AlbumScreen
import com.example.taylorlyrics.ui.FavoritesScreen
import com.example.taylorlyrics.ui.GuessScreen
import com.example.taylorlyrics.ui.HomeScreen
import com.example.taylorlyrics.ui.LyricsScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme()) {
                LyricsApp()
            }
        }
    }
}

/** 导航目标 */
private sealed class Screen {
    object Home : Screen()
    object Favorites : Screen()
    object Guess : Screen()
    data class Album(val name: String) : Screen()
    data class Lyrics(val track: Track, val keyword: String) : Screen()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LyricsApp() {
    val context: Context = LocalContext.current
    val repository = remember(context) { LyricsRepository.get(context).also { it.warmUp() } }

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

    // 系统返回键：回到上一个界面；只有在主页时才交给系统（退出应用）
    BackHandler(enabled = backStack.size > 1) { back() }

    when (val s = currentScreen) {
        is Screen.Home -> HomeScreen(
            repository = repository,
            onOpenAlbum = { navigate(Screen.Album(it)) },
            onOpenTrack = { t, q -> navigate(Screen.Lyrics(t, q)) },
            onOpenFavorites = { navigate(Screen.Favorites) },
            onOpenGuess = { navigate(Screen.Guess) },
            onSurprise = { navigate(Screen.Lyrics(repository.randomTrack(), "")) },
        )

        is Screen.Favorites -> FavoritesScreen(
            repository = repository,
            onBack = { back() },
            onOpenTrack = { t, q -> navigate(Screen.Lyrics(t, q)) },
        )

        is Screen.Guess -> GuessScreen(
            repository = repository,
            onBack = { back() },
            onOpenTrack = { t, q -> navigate(Screen.Lyrics(t, q)) },
        )

        is Screen.Album -> AlbumScreen(
            repository = repository,
            albumName = s.name,
            onBack = { back() },
            onOpenTrack = { t, q -> navigate(Screen.Lyrics(t, q)) },
        )

        is Screen.Lyrics -> LyricsScreen(
            repository = repository,
            track = s.track,
            initialTerm = s.keyword,
            onBack = { back() },
            onToggleFav = {
                val added = Favorites.toggle(context, s.track)
                Toast.makeText(
                    context,
                    if (added) "已收藏 ❤ ${s.track.track}" else "已取消收藏",
                    Toast.LENGTH_SHORT
                ).show()
            },
        )
    }
}