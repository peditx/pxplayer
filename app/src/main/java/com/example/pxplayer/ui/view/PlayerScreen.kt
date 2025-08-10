package com.example.pxplayer.ui.view

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.pxplayer.R
import com.example.pxplayer.data.ConnectionStatus
import com.example.pxplayer.ui.viewmodel.PlayerViewModel
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val uiState by viewModel.uiState.collectAsState()
    val pairedDevices by viewModel.pairedDevices.collectAsState()
    val discoveredDevices by viewModel.discoveredDevices.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showBottomSheet by remember { mutableStateOf(false) }

    // این بخش فقط یک بار هنگام ساخته شدن صفحه اجرا می‌شود تا لیست دستگاه‌های جفت‌شده را بگیرد
    LaunchedEffect(Unit) {
        viewModel.fetchPairedDevices()
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        // با توجه به وضعیت اتصال، بین صفحه پلیر و صفحه لیست دستگاه‌ها جابجا می‌شود
        Crossfade(targetState = uiState.connectionStatus, label = "main-crossfade") { status ->
            when (status) {
                ConnectionStatus.CONNECTED -> {
                    PlayerContent(
                        uiState = uiState,
                        viewModel = viewModel,
                        onEqClick = { showBottomSheet = true }
                    )
                }
                else -> { // DISCONNECTED or CONNECTING
                    DeviceListScreen(
                        pairedDevices = pairedDevices,
                        discoveredDevices = discoveredDevices,
                        onDeviceClick = { device ->
                            if (device.bondState == android.bluetooth.BluetoothDevice.BOND_BONDED) {
                                viewModel.connectToDevice(device) // اگر جفت شده، متصل شو
                            } else {
                                viewModel.pairDevice(device) // اگر نشده، ابتدا جفت شو
                            }
                        },
                        isConnecting = status == ConnectionStatus.CONNECTING,
                        isScanning = isScanning,
                        onScanClicked = {
                            if (isScanning) viewModel.cancelDiscovery() else viewModel.startDiscovery()
                        }
                    )
                }
            }
        }

        // اگر دکمه اکولایزر زده شود، این صفحه از پایین باز می‌شود
        if (showBottomSheet) {
            ModalBottomSheet(
                onDismissRequest = { showBottomSheet = false },
                sheetState = sheetState
            ) {
                EqualizerSheet(
                    eqSettings = uiState.eqSettings,
                    onEnabledChange = viewModel::setEqEnabled,
                    onBandLevelChange = viewModel::setBandLevel
                )
            }
        }
    }
}

@Composable
fun PlayerContent(
    uiState: com.example.pxplayer.data.PlayerState,
    viewModel: PlayerViewModel,
    onEqClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AlbumArt(
            albumArtUri = uiState.trackInfo.albumArtUri?.toString(),
            modifier = Modifier
                .fillMaxHeight()
                .aspectRatio(1f)
        )
        Spacer(modifier = Modifier.width(32.dp))
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TrackDetails(
                title = uiState.trackInfo.title,
                artist = uiState.trackInfo.artist
            )
            Column {
                SeekBarSection(
                    currentPosition = uiState.currentPosition,
                    totalDuration = uiState.trackInfo.duration,
                    onSeek = viewModel::onSeek
                )
                Spacer(modifier = Modifier.height(24.dp))
                PlayerControls(
                    isPlaying = uiState.isPlaying,
                    onPlayPauseClick = viewModel::onPlayPauseClick,
                    onNextClick = viewModel::onNextClick,
                    onPreviousClick = viewModel::onPreviousClick,
                    onEqClick = onEqClick
                )
            }
        }
    }
}

@Composable
fun AlbumArt(albumArtUri: String?, modifier: Modifier = Modifier) {
    AsyncImage(model = albumArtUri, placeholder = painterResource(id = R.drawable.ic_album_placeholder), error = painterResource(id = R.drawable.ic_album_placeholder), contentDescription = "Album Art", contentScale = ContentScale.Crop, modifier = modifier.clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceVariant))
}
@Composable
fun TrackDetails(title: String, artist: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = artist, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable
fun SeekBarSection(currentPosition: Long, totalDuration: Long, onSeek: (Float) -> Unit) {
    Column {
        Slider(value = currentPosition.toFloat(), onValueChange = onSeek, valueRange = 0f..totalDuration.toFloat().coerceAtLeast(0f), modifier = Modifier.fillMaxWidth())
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(text = formatTime(currentPosition), style = MaterialTheme.typography.labelMedium)
            Text(text = formatTime(totalDuration), style = MaterialTheme.typography.labelMedium)
        }
    }
}
@Composable
fun PlayerControls(isPlaying: Boolean, onPlayPauseClick: () -> Unit, onNextClick: () -> Unit, onPreviousClick: () -> Unit, onEqClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        PlayerButton(icon = Icons.Default.SkipPrevious, onClick = onPreviousClick)
        LargePlayerButton(icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, onClick = onPlayPauseClick)
        PlayerButton(icon = Icons.Default.SkipNext, onClick = onNextClick)
        PlayerButton(icon = Icons.Default.Equalizer, onClick = onEqClick)
    }
}
@Composable
fun PlayerButton(icon: ImageVector, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(72.dp)) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.fillMaxSize(0.6f))
    }
}
@Composable
fun LargePlayerButton(icon: ImageVector, onClick: () -> Unit) {
    Button(onClick = onClick, modifier = Modifier.size(88.dp), shape = CircleShape, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Icon(imageVector = icon, contentDescription = "Play/Pause", modifier = Modifier.fillMaxSize(0.7f), tint = MaterialTheme.colorScheme.onPrimary)
    }
}
private fun formatTime(millis: Long): String {
    if (millis < 0) return "00:00"
    val minutes = TimeUnit.MILLISECONDS.toMinutes(millis)
    val seconds = TimeUnit.MILLISECONDS.toSeconds(millis) - TimeUnit.MINUTES.toSeconds(minutes)
    return String.format("%02d:%02d", minutes, seconds)
}

