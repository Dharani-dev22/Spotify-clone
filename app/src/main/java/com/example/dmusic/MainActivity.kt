package com.example.dmusic

import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

val DarkGreen = Color(0xFF1DB954)
val BackgroundDark = Color(0xFF121212)
val SurfaceDark = Color(0xFF282828)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val authManager = remember { AuthManager(context) }

            val savedName = remember { authManager.getLoggedInUser() }
            var isAuthenticated by remember { mutableStateOf(savedName != null) }
            var loggedInUserName by remember { mutableStateOf(savedName ?: "") }

            MaterialTheme(
                colorScheme = darkColorScheme(
                    background = BackgroundDark,
                    surface = SurfaceDark,
                    primary = DarkGreen
                )
            ) {
                if (isAuthenticated) {
                    DMusicApp(
                        userName = loggedInUserName,
                        onLogout = {
                            authManager.clearSession()
                            isAuthenticated = false
                            loggedInUserName = ""
                        }
                    )
                } else {
                    AuthSession(
                        onAuthSuccess = { name ->
                            authManager.saveSession(name)
                            loggedInUserName = name
                            isAuthenticated = true
                        }
                    )
                }
            }
        }
    }
}

data class Track(val id: String, val name: String, val artist_name: String, val image: String, val audio: String)
data class JamendoResponse(val results: List<Track>)

interface JamendoService {
    @GET("v3.0/tracks/")
    suspend fun getTracks(
        @Query("client_id") clientId: String = "a484e8ae",
        @Query("format") format: String = "json",
        @Query("limit") limit: Int = 20,
        @Query("order") order: String = "popularity_total",
        @Query("tags") tags: String? = null,
        @Query("search") search: String? = null
    ): JamendoResponse
}

object RetrofitInstance {
    val api: JamendoService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.jamendo.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(JamendoService::class.java)
    }
}

fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

class MusicViewModel(application: Application) : AndroidViewModel(application) {
    var trackList by mutableStateOf<List<Track>>(emptyList())
    var searchResults by mutableStateOf<List<Track>>(emptyList())
    val playlistTracks = mutableStateListOf<Track>()
    val downloadedTracks = mutableStateListOf<Track>()

    var isLoading by mutableStateOf(true)
    var isSearchLoading by mutableStateOf(false)
    var currentTrack by mutableStateOf<Track?>(null)
    var isPlaying by mutableStateOf(false)
    var selectedCategory by mutableStateOf("All")
    var currentPosition by mutableLongStateOf(0L)
    var totalDuration by mutableLongStateOf(0L)
    var currentQueue by mutableStateOf<List<Track>>(emptyList())
    var isShuffleEnabled by mutableStateOf(false)
    var repeatMode by mutableIntStateOf(Player.REPEAT_MODE_OFF)

    private val player = ExoPlayer.Builder(application).build()

    init {
        fetchSongs()
        setupPlayerListener()
        startPositionTracker()
    }

    private fun setupPlayerListener() {
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) totalDuration = player.duration.coerceAtLeast(0L)
            }
            override fun onIsPlayingChanged(isPlayingState: Boolean) { isPlaying = isPlayingState }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                mediaItem?.mediaId?.let { id -> currentTrack = currentQueue.find { it.id == id } }
            }
        })
    }

    private fun startPositionTracker() {
        viewModelScope.launch {
            while (isActive) {
                if (isPlaying) currentPosition = player.currentPosition
                delay(1000L)
            }
        }
    }

    fun fetchSongs(category: String = "All") {
        selectedCategory = category
        val tagParam = if (category == "All") null else category.lowercase()
        viewModelScope.launch {
            isLoading = true
            try {
                val response = RetrofitInstance.api.getTracks(tags = tagParam)
                trackList = response.results
            } catch (e: Exception) { e.printStackTrace() } finally { isLoading = false }
        }
    }

    fun searchSongs(query: String) {
        if (query.isBlank()) { searchResults = emptyList(); return }
        viewModelScope.launch {
            isSearchLoading = true
            try {
                val response = RetrofitInstance.api.getTracks(search = query)
                searchResults = response.results
            } catch (e: Exception) { e.printStackTrace() } finally { isSearchLoading = false }
        }
    }

    fun togglePlaylist(track: Track) {
        if (playlistTracks.any { it.id == track.id }) playlistTracks.removeAll { it.id == track.id }
        else playlistTracks.add(track)
    }

    fun toggleDownload(track: Track) {
        if (downloadedTracks.any { it.id == track.id }) downloadedTracks.removeAll { it.id == track.id }
        else downloadedTracks.add(track)
    }

    fun playTrackList(tracks: List<Track>, startIndex: Int) {
        currentQueue = tracks
        currentTrack = tracks[startIndex]
        val mediaItems = tracks.map { MediaItem.Builder().setUri(it.audio).setMediaId(it.id).build() }
        player.setMediaItems(mediaItems, startIndex, 0L)
        player.prepare()
        player.play()
        isPlaying = true
    }

    fun togglePlayPause() { if (player.isPlaying) player.pause() else player.play() }
    fun skipNext() { if (player.hasNextMediaItem()) player.seekToNext() }
    fun skipPrevious() { if (player.hasPreviousMediaItem()) player.seekToPrevious() }
    fun seekTo(position: Long) { player.seekTo(position); currentPosition = position }
    fun toggleShuffle() { isShuffleEnabled = !isShuffleEnabled; player.shuffleModeEnabled = isShuffleEnabled }
    fun toggleRepeat() {
        repeatMode = when (repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = repeatMode
    }
    override fun onCleared() { super.onCleared(); player.release() }
}

sealed class BottomNavItem(val route: String, val icon: ImageVector, val label: String) {
    object Home : BottomNavItem("home", Icons.Default.Home, "Home")
    object Search : BottomNavItem("search", Icons.Default.Search, "Search")
    object Library : BottomNavItem("library", Icons.Default.LibraryMusic, "Playlist")
    object Profile : BottomNavItem("profile", Icons.Default.Person, "Profile")
}

@Composable
fun DMusicApp(musicViewModel: MusicViewModel = viewModel(), userName: String, onLogout: () -> Unit) {
    val navController = rememberNavController()
    var isFullScreenPlayerOpen by remember { mutableStateOf(false) }

    BackHandler(enabled = isFullScreenPlayerOpen) { isFullScreenPlayerOpen = false }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    musicViewModel.currentTrack?.let { track ->
                        BottomPlayer(
                            track = track,
                            isPlaying = musicViewModel.isPlaying,
                            isPlaylist = musicViewModel.playlistTracks.any { it.id == track.id },
                            pos = musicViewModel.currentPosition,
                            dur = musicViewModel.totalDuration,
                            onPlay = { musicViewModel.togglePlayPause() },
                            onPlaylistToggle = { musicViewModel.togglePlaylist(track) },
                            onSeek = { musicViewModel.seekTo(it) },
                            onClick = { isFullScreenPlayerOpen = true }
                        )
                    }
                    BottomNavigationBar(navController = navController)
                }
            }
        ) { paddingValues ->
            NavHost(
                navController = navController,
                startDestination = BottomNavItem.Home.route,
                modifier = Modifier.padding(paddingValues)
            ) {
                composable(BottomNavItem.Home.route) { HomeScreen(userName, musicViewModel, navController) }
                composable(BottomNavItem.Search.route) { SearchScreen(musicViewModel) }
                composable(BottomNavItem.Library.route) { LibraryScreen(musicViewModel) }
                composable(BottomNavItem.Profile.route) { ProfileNavHost(userName, onLogout, musicViewModel) }
            }
        }

        AnimatedVisibility(
            visible = isFullScreenPlayerOpen && musicViewModel.currentTrack != null,
            enter = slideInVertically(initialOffsetY = { it }),
            exit = slideOutVertically(targetOffsetY = { it })
        ) {
            musicViewModel.currentTrack?.let { track ->
                FullScreenPlayer(
                    track = track,
                    isPlaying = musicViewModel.isPlaying,
                    isPlaylist = musicViewModel.playlistTracks.any { it.id == track.id },
                    isDownloaded = musicViewModel.downloadedTracks.any { it.id == track.id },
                    pos = musicViewModel.currentPosition,
                    dur = musicViewModel.totalDuration,
                    shuffle = musicViewModel.isShuffleEnabled,
                    repeat = musicViewModel.repeatMode,
                    onClose = { isFullScreenPlayerOpen = false },
                    onPlay = { musicViewModel.togglePlayPause() },
                    onPlaylistToggle = { musicViewModel.togglePlaylist(track) },
                    onDownloadToggle = { musicViewModel.toggleDownload(track) },
                    onNext = { musicViewModel.skipNext() },
                    onPrev = { musicViewModel.skipPrevious() },
                    onSeek = { musicViewModel.seekTo(it) },
                    onShuffle = { musicViewModel.toggleShuffle() },
                    onRepeat = { musicViewModel.toggleRepeat() }
                )
            }
        }
    }
}

@Composable
fun BottomNavigationBar(navController: NavHostController) {
    val items = listOf(BottomNavItem.Home, BottomNavItem.Search, BottomNavItem.Library, BottomNavItem.Profile)
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    NavigationBar(containerColor = BackgroundDark) {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(imageVector = item.icon, contentDescription = item.label) },
                label = { Text(text = item.label) },
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(navController.graph.startDestinationId) { saveState = true }
                        launchSingleTop = true; restoreState = true
                    }
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = DarkGreen, unselectedIconColor = Color.Gray,
                    selectedTextColor = DarkGreen, unselectedTextColor = Color.Gray, indicatorColor = SurfaceDark
                )
            )
        }
    }
}

@Composable
fun HomeScreen(userName: String, musicViewModel: MusicViewModel, navController: NavHostController) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        Column(modifier = Modifier.padding(16.dp)) {
            TopHeader(userName = userName, onProfileClick = { navController.navigate(BottomNavItem.Profile.route) })
            Spacer(modifier = Modifier.height(16.dp))
            CategoryChips(selected = musicViewModel.selectedCategory, onSelected = { musicViewModel.fetchSongs(it) })
            Spacer(modifier = Modifier.height(24.dp))
            SectionTitle(title = "Made for You")
            if (musicViewModel.isLoading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DarkGreen) }
            } else {
                HorizontalAlbumList(tracks = musicViewModel.trackList) { musicViewModel.playTrackList(musicViewModel.trackList, it) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(musicViewModel: MusicViewModel) {
    var query by remember { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        Column(modifier = Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it; if (it.length >= 2) musicViewModel.searchSongs(it) },
                modifier = Modifier.fillMaxWidth(), placeholder = { Text(text = "Search songs...") },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = DarkGreen, focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { musicViewModel.searchSongs(query); keyboardController?.hide() }),
                singleLine = true, leadingIcon = { Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = Color.White) }
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (musicViewModel.isSearchLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = DarkGreen) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(musicViewModel.searchResults.size) { index ->
                        TrackListItem(track = musicViewModel.searchResults[index]) { musicViewModel.playTrackList(musicViewModel.searchResults, index) }
                    }
                }
            }
        }
    }
}

@Composable
fun LibraryScreen(musicViewModel: MusicViewModel) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = "Your Playlist", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (musicViewModel.playlistTracks.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(text = "No songs in your playlist.", color = Color.Gray) }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    items(musicViewModel.playlistTracks.size) { index ->
                        TrackListItem(track = musicViewModel.playlistTracks[index]) { musicViewModel.playTrackList(musicViewModel.playlistTracks.toList(), index) }
                    }
                }
            }
        }
    }
}

@Composable
fun TopHeader(userName: String, onProfileClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text = "Welcome, $userName", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(SurfaceDark).clickable { onProfileClick() }, contentAlignment = Alignment.Center) {
            Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = DarkGreen)
        }
    }
}

@Composable
fun CategoryChips(selected: String, onSelected: (String) -> Unit) {
    val categories = listOf("All", "Pop", "Rock", "Electronic", "Jazz")
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(categories) { category ->
            val isSelected = category == selected
            Box(modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (isSelected) DarkGreen else SurfaceDark).clickable { onSelected(category) }.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(text = category, color = if (isSelected) Color.Black else Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun SectionTitle(title: String) { Text(text = title, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(bottom = 12.dp)) }

@Composable
fun HorizontalAlbumList(tracks: List<Track>, onTrackClick: (Int) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        items(tracks.size) { index ->
            val track = tracks[index]
            Column(modifier = Modifier.width(140.dp).clickable { onTrackClick(index) }) {
                AsyncImage(
                    model = track.image,
                    contentDescription = track.name,
                    modifier = Modifier.size(140.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceDark),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = track.name, color = Color.White, fontSize = 14.sp, maxLines = 1, fontWeight = FontWeight.Bold)
                Text(text = track.artist_name, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
fun TrackListItem(track: Track, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(SurfaceDark, RoundedCornerShape(8.dp)).clickable { onClick() }.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(
            model = track.image,
            contentDescription = null,
            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = track.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(text = track.artist_name, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
fun BottomPlayer(track: Track, isPlaying: Boolean, isPlaylist: Boolean, pos: Long, dur: Long, onPlay: () -> Unit, onPlaylistToggle: () -> Unit, onSeek: (Long) -> Unit, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color(0xFF333333)).clickable { onClick() }) {
        Row(modifier = Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = track.image,
                contentDescription = null,
                modifier = Modifier.size(50.dp).clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.name, color = Color.White, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = track.artist_name, color = Color.Gray, fontSize = 12.sp, maxLines = 1)
            }
            IconButton(onClick = onPlaylistToggle) { Icon(imageVector = if (isPlaylist) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isPlaylist) DarkGreen else Color.White) }
            IconButton(onClick = onPlay) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        }
        Slider(value = pos.toFloat(), onValueChange = { onSeek(it.toLong()) }, modifier = Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 8.dp), valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = DarkGreen, activeTrackColor = DarkGreen))
    }
}

@Composable
fun FullScreenPlayer(track: Track, isPlaying: Boolean, isPlaylist: Boolean, isDownloaded: Boolean, pos: Long, dur: Long, shuffle: Boolean, repeat: Int, onClose: () -> Unit, onPlay: () -> Unit, onPlaylistToggle: () -> Unit, onDownloadToggle: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit, onSeek: (Long) -> Unit, onShuffle: () -> Unit, onRepeat: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2E332E), BackgroundDark))).padding(24.dp)) {
        IconButton(onClick = onClose) { Icon(imageVector = Icons.Default.KeyboardArrowDown, contentDescription = null, tint = Color.White, modifier = Modifier.size(32.dp)) }
        Spacer(modifier = Modifier.weight(1f))
        AsyncImage(
            model = track.image,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(8.dp)),
            contentScale = ContentScale.Crop
        )
        Spacer(modifier = Modifier.height(32.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = track.name, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(text = track.artist_name, color = Color.Gray, fontSize = 16.sp, maxLines = 1)
            }
            Row {
                IconButton(onClick = onDownloadToggle) { Icon(imageVector = if (isDownloaded) Icons.Default.CloudDone else Icons.Default.CloudDownload, contentDescription = null, tint = if (isDownloaded) DarkGreen else Color.White) }
                IconButton(onClick = onPlaylistToggle) { Icon(imageVector = if (isPlaylist) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null, tint = if (isPlaylist) DarkGreen else Color.White) }
            }
        }
        Slider(value = pos.toFloat(), onValueChange = { onSeek(it.toLong()) }, modifier = Modifier.fillMaxWidth(), valueRange = 0f..dur.toFloat().coerceAtLeast(1f), colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onShuffle) { Icon(imageVector = Icons.Default.Shuffle, contentDescription = null, tint = if (shuffle) DarkGreen else Color.White) }
            IconButton(onClick = onPrev) { Icon(imageVector = Icons.Default.SkipPrevious, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            Box(modifier = Modifier.size(72.dp).clip(CircleShape).background(Color.White).clickable { onPlay() }, contentAlignment = Alignment.Center) { Icon(imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(40.dp)) }
            IconButton(onClick = onNext) { Icon(imageVector = Icons.Default.SkipNext, contentDescription = null, tint = Color.White, modifier = Modifier.size(40.dp)) }
            IconButton(onClick = onRepeat) { Icon(imageVector = if (repeat == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat, contentDescription = null, tint = if (repeat == Player.REPEAT_MODE_OFF) Color.White else DarkGreen) }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}