package si.nicofi.discgolfeye.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import si.nicofi.discgolfeye.client.DiscGolfClient
import si.nicofi.discgolfeye.client.DownloadService
import si.nicofi.discgolfeye.shared.DeviceStatus
import si.nicofi.discgolfeye.shared.VideoFileInfo
import si.nicofi.discgolfeye.ui.components.VideoPlayer
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClientScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connectionStatus by remember { mutableStateOf("Niepołączony") }
    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var gatewayIp by remember { mutableStateOf<String?>(null) }

    var serverStatus by remember { mutableStateOf<DeviceStatus?>(null) }
    var videoFiles by remember { mutableStateOf<List<VideoFileInfo>>(emptyList()) }
    var selectedVideo by remember { mutableStateOf<VideoFileInfo?>(null) }

    val client = remember { DiscGolfClient() }

    DisposableEffect(Unit) {
        onDispose { client.close() }
    }

    // Auto-refresh gdy połączony
    LaunchedEffect(isConnected) {
        while (isConnected) {
            // Pobierz status
            client.getStatus().onSuccess { status ->
                serverStatus = status
            }
            // Pobierz listę wideo
            client.getVideos().onSuccess { videos ->
                videoFiles = videos
            }
            delay(5000) // Odśwież co 5 sekund
        }
    }

    // Jeśli wybrano wideo - pokaż odtwarzacz
    if (selectedVideo != null) {
        VideoPlayerScreen(
            videoUrl = client.getVideoStreamUrl(selectedVideo!!.filename) ?: "",
            videoName = selectedVideo!!.filename,
            onBack = { selectedVideo = null }
        )
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Visibility,
                contentDescription = "Oglądacz",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tryb Oglądacza",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status połączenia
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Status:",
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text(
                                text = connectionStatus,
                                style = MaterialTheme.typography.bodyLarge,
                                color = if (isConnected) MaterialTheme.colorScheme.primary
                                       else MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    if (!isConnected) {
                        Button(
                            onClick = {
                                isLoading = true
                                scope.launch {
                                    val ip = DiscGolfClient.getGatewayIp(context)
                                    gatewayIp = ip

                                    if (ip == null) {
                                        connectionStatus = "Brak Wi-Fi"
                                        isLoading = false
                                        return@launch
                                    }

                                    client.ping(ip).fold(
                                        onSuccess = {
                                            connectionStatus = "Połączono ($ip)"
                                            isConnected = true
                                        },
                                        onFailure = {
                                            connectionStatus = "Błąd: ${it.message}"
                                        }
                                    )
                                    isLoading = false
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Text("Połącz")
                        }
                    }
                }

                // Szczegóły serwera gdy połączony
                if (isConnected && serverStatus != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Divider()
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        StatusItem(
                            label = "Bateria",
                            value = "${serverStatus!!.batteryLevel}%",
                            warning = serverStatus!!.batteryLevel < 20
                        )
                        StatusItem(
                            label = "Temp",
                            value = "${serverStatus!!.batteryTemp}°C",
                            warning = serverStatus!!.batteryTemp > 42
                        )
                        StatusItem(
                            label = "Dysk",
                            value = "${String.format("%.1f", serverStatus!!.storageFreeGb)}GB",
                            warning = serverStatus!!.storageFreeGb < 2
                        )
                        StatusItem(
                            label = "Status",
                            value = if (serverStatus!!.isRecording) "REC" else "IDLE",
                            warning = !serverStatus!!.isRecording
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Lista nagrań
        if (isConnected) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Nagrania (${videoFiles.size})",
                    style = MaterialTheme.typography.titleMedium
                )

                TextButton(
                    onClick = {
                        scope.launch {
                            client.triggerFlush()
                            delay(500)
                            client.getVideos().onSuccess { videos ->
                                videoFiles = videos
                            }
                        }
                    }
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Odśwież")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Odśwież")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (videoFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Brak nagrań.\nPoczekaj minutę na pierwszy chunk.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(videoFiles) { video ->
                        VideoItem(
                            video = video,
                            baseUrl = client.getServerBaseUrl() ?: "",
                            onClick = { selectedVideo = video }
                        )
                    }
                }
            }
        } else {
            // Instrukcje gdy niepołączony
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "1. Włącz Hotspot na telefonie-kamerze\n" +
                           "2. Połącz się z tym Hotspotem\n" +
                           "3. Kliknij \"Połącz\"",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun StatusItem(
    label: String,
    value: String,
    warning: Boolean = false
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (warning) MaterialTheme.colorScheme.error
                   else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun VideoItem(
    video: VideoFileInfo,
    baseUrl: String,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    // Oblicz czas względny lub absolutny
    val timeDisplay = remember(video.timestamp) {
        val now = System.currentTimeMillis()
        val diffMs = now - video.timestamp
        val diffSeconds = diffMs / 1000
        val diffMinutes = diffSeconds / 60

        when {
            diffMinutes < 1 -> "${diffSeconds}s temu"
            diffMinutes < 60 -> "${diffMinutes}min temu"
            else -> dateFormat.format(Date(video.timestamp))
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Miniaturka
            Box(
                modifier = Modifier
                    .size(80.dp, 60.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.DarkGray),
                contentAlignment = Alignment.Center
            ) {
                if (video.thumbUrl != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data("$baseUrl${video.thumbUrl}")
                            .crossfade(true)
                            .build(),
                        contentDescription = "Miniaturka ${video.filename}",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        Icons.Default.Videocam,
                        contentDescription = "Video",
                        tint = Color.Gray,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Info
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = timeDisplay,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", video.sizeMb)} MB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Play button
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = "Odtwórz",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
    }
}


@Composable
private fun VideoPlayerScreen(
    videoUrl: String,
    videoName: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // Nazwa pliku z datą
    val downloadFilename = remember(videoName) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_", Locale.getDefault())
        val date = dateFormat.format(Date())
        "$date$videoName"
    }

    // Obsługa gestu cofania
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Header z przyciskami
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Wróć",
                    tint = Color.White
                )
            }

            Text(
                text = videoName,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // Przycisk pobierania
            IconButton(
                onClick = {
                    DownloadService.startDownload(context, videoUrl, downloadFilename)
                    Toast.makeText(context, "Pobieranie w tle...", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    Icons.Default.Download,
                    contentDescription = "Pobierz",
                    tint = Color.White
                )
            }
        }

        // Player
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
        ) {
            VideoPlayer(
                videoUrl = videoUrl,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}



