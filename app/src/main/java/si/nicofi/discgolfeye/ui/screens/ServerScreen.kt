package si.nicofi.discgolfeye.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import si.nicofi.discgolfeye.server.CameraInfo
import si.nicofi.discgolfeye.server.CameraPreferences
import si.nicofi.discgolfeye.server.ServerService

@Composable
fun ServerScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isServerRunning by remember { mutableStateOf(false) }
    var isStopping by remember { mutableStateOf(false) }
    var permissionGranted by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }

    // Wybór kamery
    val cameraPreferences = remember { CameraPreferences(context) }
    val availableCameras = remember { CameraInfo.detectCameras(context) }
    var selectedCameraId by remember {
        mutableStateOf(
            cameraPreferences.selectedCameraId
                ?: availableCameras.firstOrNull { !it.isFront }?.id
                ?: availableCameras.firstOrNull()?.id
        )
    }
    var showCameraDialog by remember { mutableStateOf(false) }

    val selectedCamera = availableCameras.find { it.id == selectedCameraId }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissionGranted = permissions[Manifest.permission.CAMERA] == true &&
                           permissions[Manifest.permission.RECORD_AUDIO] == true

        // Jeśli uprawnienia przyznane, uruchom serwer
        if (permissionGranted) {
            val intent = Intent(context, ServerService::class.java).apply {
                action = ServerService.ACTION_START_SERVER
            }
            context.startForegroundService(intent)
            isServerRunning = true
        }
    }

    val serverStatus = if (isServerRunning) "Działa na :${ServerService.PORT}" else "Zatrzymany"

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Videocam,
                contentDescription = "Kamera",
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Tryb Kamery",
                style = MaterialTheme.typography.headlineMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Wybór kamery (tylko gdy serwer nie działa)
        if (!isServerRunning) {
            OutlinedCard(
                modifier = Modifier.fillMaxWidth(),
                onClick = { showCameraDialog = true }
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Kamera",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = selectedCamera?.displayName ?: "Nie wybrano",
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                    Icon(
                        Icons.Default.CameraAlt,
                        contentDescription = "Zmień kamerę"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Status kamery - duży box zamiast podglądu
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            if (isServerRunning) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Status nagrywania
                    Box(
                        modifier = Modifier
                            .background(
                                Color.Red.copy(alpha = 0.8f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            text = "● REC",
                            color = Color.White,
                            style = MaterialTheme.typography.headlineMedium
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "Kamera nagrywa w tle.\nMożesz teraz włożyć telefon do plecaka.",
                        color = Color.White.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Połącz się z drugim telefonem\nprzez Hotspot Wi-Fi",
                        color = Color.White.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                Text(
                    text = if (!permissionGranted) "Brak uprawnień kamery" else "Uruchom serwer aby nagrywać",
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Status serwera:",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = serverStatus,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (isServerRunning) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                }

                if (!permissionGranted) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "Ostrzeżenie",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (isServerRunning && !isStopping) {
                    // Zatrzymaj serwer
                    isStopping = true

                    scope.launch {
                        val intent = Intent(context, ServerService::class.java).apply {
                            action = ServerService.ACTION_STOP_SERVER
                        }
                        context.startService(intent)

                        // Poczekaj na zatrzymanie
                        delay(2000)
                        isServerRunning = false
                        isStopping = false
                    }
                } else if (!isServerRunning && !isStopping) {
                    // Sprawdź uprawnienia
                    if (permissionGranted) {
                        val intent = Intent(context, ServerService::class.java).apply {
                            action = ServerService.ACTION_START_SERVER
                        }
                        context.startForegroundService(intent)
                        isServerRunning = true
                    } else {
                        // Poproś o uprawnienia
                        permissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.CAMERA,
                                Manifest.permission.RECORD_AUDIO
                            )
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            enabled = !isStopping,
            colors = if (isServerRunning || isStopping) {
                ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            } else {
                ButtonDefaults.buttonColors()
            }
        ) {
            if (isStopping) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Zatrzymywanie...")
            } else {
                Text(if (isServerRunning) "Zatrzymaj serwer" else "Uruchom serwer")
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Włącz Hotspot Wi-Fi, uruchom serwer i połącz drugi telefon.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    // Dialog wyboru kamery
    if (showCameraDialog) {
        AlertDialog(
            onDismissRequest = { showCameraDialog = false },
            title = { Text("Wybierz kamerę") },
            text = {
                Column {
                    availableCameras.forEach { camera ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable {
                                    selectedCameraId = camera.id
                                    cameraPreferences.selectedCameraId = camera.id
                                    showCameraDialog = false
                                }
                                .padding(vertical = 12.dp, horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedCameraId == camera.id,
                                onClick = null // onClick obsługiwany przez Row
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(camera.displayName)
                        }
                    }

                    if (availableCameras.isEmpty()) {
                        Text(
                            "Nie wykryto kamer",
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showCameraDialog = false }) {
                    Text("Anuluj")
                }
            }
        )
    }
}

