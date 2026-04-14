package com.example.dmusic

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun ProfileNavHost(userName: String, onLogout: () -> Unit, musicViewModel: MusicViewModel) {
    val profileNavController = rememberNavController()
    NavHost(navController = profileNavController, startDestination = "profile_main") {
        composable("profile_main") { ProfileMainScreen(userName, onLogout) { profileNavController.navigate(it) } }
        composable("settings") { SettingsScreen { profileNavController.popBackStack() } }
        composable("history") { HistoryScreen(musicViewModel) { profileNavController.popBackStack() } }
        composable("downloads") { DownloadsScreen(musicViewModel) { profileNavController.popBackStack() } }
    }
}

@Composable
fun ProfileMainScreen(userName: String, onLogout: () -> Unit, onNavigate: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = BackgroundDark) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            item {
                Spacer(modifier = Modifier.height(48.dp))
                Box(modifier = Modifier.size(120.dp).clip(CircleShape).background(SurfaceDark), Alignment.Center) {
                    Icon(imageVector = Icons.Default.Person, contentDescription = null, tint = DarkGreen, modifier = Modifier.size(80.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = userName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(modifier = Modifier.height(32.dp))
            }
            item {
                ProfileOption(icon = Icons.Default.Settings, label = "Settings") { onNavigate("settings") }
                ProfileOption(icon = Icons.Default.History, label = "Listening History") { onNavigate("history") }
                ProfileOption(icon = Icons.Default.CloudDownload, label = "Downloads") { onNavigate("downloads") }
                Spacer(modifier = Modifier.height(48.dp))
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)), shape = RoundedCornerShape(8.dp)) {
                    Text(text = "Logout", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    var gapless by remember { mutableStateOf(false) }
    var automix by remember { mutableStateOf(true) }
    var crossfade by remember { mutableFloatStateOf(0f) }
    var autoplay by remember { mutableStateOf(true) }
    var mono by remember { mutableStateOf(false) }
    var broadcast by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(text = "Playback", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)) }, containerColor = BackgroundDark) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).padding(16.dp)) {
            item {
                Text(text = "Track transitions", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SettingToggle(title = "Gapless playback", desc = "Removes gaps between tracks.", checked = gapless) { gapless = it }
                SettingToggle(title = "Automix", desc = "Seamless transitions on playlists.", checked = automix) { automix = it }
                Text(text = "Crossfade", color = Color.White, modifier = Modifier.padding(top = 8.dp))
                Slider(value = crossfade, onValueChange = { crossfade = it }, modifier = Modifier.fillMaxWidth(), valueRange = 0f..12f, colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = Color.White))
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = "Listening controls", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SettingToggle(title = "Autoplay", desc = "Play similar content when music ends.", checked = autoplay) { autoplay = it }
                SettingToggle(title = "Mono audio", desc = "Both speakers play the same audio.", checked = mono) { mono = it }
                SettingToggle(title = "Device broadcast status", desc = "Allow other apps to see music info.", checked = broadcast) { broadcast = it }
            }
        }
    }
}

@Composable
fun SettingToggle(title: String, desc: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) { Text(text = title, color = Color.White, fontSize = 16.sp); Text(text = desc, color = Color.Gray, fontSize = 12.sp) }
        Switch(checked = checked, onCheckedChange = onChecked, colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = DarkGreen))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(musicViewModel: MusicViewModel, onBack: () -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text(text = "Listening History", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)) }, containerColor = BackgroundDark) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            if (musicViewModel.trackList.isEmpty()) item { Text(text = "No history.", color = Color.Gray) }
            else items(musicViewModel.trackList.take(10)) { track -> TrackListItem(track = track) { musicViewModel.playTrackList(musicViewModel.trackList, musicViewModel.trackList.indexOf(track)) }; Spacer(Modifier.height(8.dp)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(musicViewModel: MusicViewModel, onBack: () -> Unit) {
    Scaffold(
        topBar = { TopAppBar(title = { Text(text = "Downloads", color = Color.White) }, navigationIcon = { IconButton(onClick = onBack) { Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null, tint = Color.White) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundDark)) },
        containerColor = BackgroundDark
    ) { padding ->
        Surface(modifier = Modifier.fillMaxSize().padding(padding), color = BackgroundDark) {
            if (musicViewModel.downloadedTracks.isEmpty()) {
                Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Icon(imageVector = Icons.Default.CloudOff, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "No downloads yet.", color = Color.White, fontWeight = FontWeight.Bold)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(musicViewModel.downloadedTracks.size) { index ->
                        TrackListItem(track = musicViewModel.downloadedTracks[index]) {
                            musicViewModel.playTrackList(musicViewModel.downloadedTracks.toList(), index)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileOption(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp).clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Icon(imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = Color.Gray)
    }
}