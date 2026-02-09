package si.nicofi.discgolfeye.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import si.nicofi.discgolfeye.client.CameraWatchdog
import si.nicofi.discgolfeye.client.DiscGolfClient
import si.nicofi.discgolfeye.client.DownloadService
import si.nicofi.discgolfeye.shared.DeviceStatus
import si.nicofi.discgolfeye.shared.VideoFileInfo
import si.nicofi.discgolfeye.ui.components.VideoPlayer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
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
    var isRefreshing by remember { mutableStateOf(false) }

    val client = remember { DiscGolfClient() }
    val watchdog = remember { CameraWatchdog(context) }
    var activeAlerts by remember { mutableStateOf<Set<CameraWatchdog.AlertType>>(emptySet()) }

    DisposableEffect(Unit) {
        onDispose {
            client.close()
            watchdog.stopMonitoring()
        }
    }

    // Uruchom Watchdog gdy połączony
    LaunchedEffect(isConnected) {
        if (isConnected) {
            watchdog.onStateChanged = { state ->
                activeAlerts = state.activeAlerts
                state.lastStatus?.let { serverStatus = it }
                // Jeśli brak statusu - ustaw null
                if (state.lastStatus == null) {
                    serverStatus = null
                }
            }
            watchdog.onConnectionLost = {
                // Reset przy utracie połączenia
                connectionStatus = "Utracono połączenie"
                isConnected = false
                serverStatus = null
                videoFiles = emptyList()
            }
            watchdog.startMonitoring(this) {
                client.getStatus()
            }
        } else {
            watchdog.stopMonitoring()
            activeAlerts = emptySet()
        }
    }

    // Pobierz listę wideo osobno (watchdog zajmuje się statusem)
    LaunchedEffect(isConnected) {
        while (isConnected) {
            client.getVideos().onSuccess { videos ->
                videoFiles = videos
            }
            delay(5000) // Odśwież co 5 sekund
        }
    }

    // Jeśli wybrano wideo - pokaż odtwarzacz
    if (selectedVideo != null) {
        VideoPlayerScreen(
            video = selectedVideo!!,
            videoUrl = client.getVideoStreamUrl(selectedVideo!!.filename) ?: "",
            onBack = { selectedVideo = null },
            onStar = { filename ->
                scope.launch {
                    client.toggleStar(filename).onSuccess { isStarred ->
                        // Odśwież listę
                        client.getVideos().onSuccess { videos ->
                            videoFiles = videos
                            // Zaktualizuj selectedVideo
                            selectedVideo = videos.find { it.filename == filename }
                        }
                    }
                }
            },
            onDelete = { filename ->
                scope.launch {
                    client.deleteVideo(filename).onSuccess {
                        // Wróć do listy i odśwież
                        selectedVideo = null
                        client.getVideos().onSuccess { videos ->
                            videoFiles = videos
                        }
                    }
                }
            }
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

        // Banner alertów
        if (activeAlerts.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier
                        .padding(12.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = when {
                            activeAlerts.any { it.name.contains("CRITICAL") } -> "⚠️ UWAGA! Sprawdź kamerę!"
                            else -> "Ostrzeżenie - sprawdź status kamery"
                        },
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

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
                            label = "Wolne",
                            value = "${String.format(Locale.US, "%.1f", serverStatus!!.storageFreeGb)}GB",
                            warning = serverStatus!!.storageFreeGb < 2
                        )
                        StatusItem(
                            label = "Użyte",
                            value = "${String.format(Locale.US, "%.2f", serverStatus!!.storageUsedGb)}GB",
                            warning = false
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
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            client.triggerFlush()
                            delay(500)
                            client.getVideos().onSuccess { videos ->
                                videoFiles = videos
                            }
                            client.getStatus().onSuccess { status ->
                                serverStatus = status
                            }
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
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

    // Czas który aktualizuje się co sekundę
    var timeDisplay by remember { mutableStateOf("") }

    LaunchedEffect(video.timestamp) {
        while (true) {
            val now = System.currentTimeMillis()
            val diffMs = now - video.timestamp
            val diffSeconds = diffMs / 1000
            val diffMinutes = diffSeconds / 60

            timeDisplay = when {
                diffMinutes < 1 -> "${diffSeconds}s temu"
                diffMinutes < 60 -> "${diffMinutes}min temu"
                else -> dateFormat.format(Date(video.timestamp))
            }

            // Aktualizuj co sekundę jeśli < 1min, co 10s jeśli < 1h, inaczej nie aktualizuj
            val delayMs = when {
                diffMinutes < 1 -> 1000L
                diffMinutes < 60 -> 10000L
                else -> break // Nie potrzebujemy aktualizować po godzinie
            }
            delay(delayMs)
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (video.isStarred) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = "Ulubione",
                            tint = Color(0xFFFFD700), // Złoty
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = timeDisplay,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
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
    video: VideoFileInfo,
    videoUrl: String,
    onBack: () -> Unit,
    onStar: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    // Lokalny state dla star - toggleuje się od razu
    var isStarred by remember(video.filename) { mutableStateOf(video.isStarred) }

    // Nazwa pliku z datą
    val downloadFilename = remember(video.filename) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd_", Locale.getDefault())
        val date = dateFormat.format(Date())
        "$date${video.filename}"
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
                text = video.filename,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )

            // Przycisk Star
            IconButton(
                onClick = {
                    isStarred = !isStarred // Toggle lokalnie od razu
                    onStar(video.filename)
                }
            ) {
                Icon(
                    if (isStarred) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = if (isStarred) "Usuń z ulubionych" else "Dodaj do ulubionych",
                    tint = if (isStarred) Color(0xFFFFD700) else Color.White
                )
            }

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

            // Menu trzykropkowe
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Więcej opcji",
                        tint = Color.White
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Usuń nagranie", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            showMenu = false
                            showDeleteDialog = true
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    )
                }
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

    // Dialog potwierdzenia usunięcia
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Usuń nagranie") },
            text = {
                Column {
                    if (isStarred) {
                        Text(
                            "⚠️ To nagranie jest oznaczone jako ulubione!",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                    Text("Czy na pewno chcesz usunąć ${video.filename}?\n\nTej operacji nie można cofnąć.")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete(video.filename)
                    }
                ) {
                    Text("Usuń", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}



