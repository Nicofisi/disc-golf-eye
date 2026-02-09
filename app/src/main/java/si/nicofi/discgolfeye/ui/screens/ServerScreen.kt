package si.nicofi.discgolfeye.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ServerScreen(
    modifier: Modifier = Modifier
) {
    var serverStatus by remember { mutableStateOf("Zatrzymany") }
    var isServerRunning by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "📹 Tryb Kamery",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(32.dp))

        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Status serwera:",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = serverStatus,
                    style = MaterialTheme.typography.headlineSmall,
                    color = if (isServerRunning) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                // TODO: Uruchom/zatrzymaj serwer
                isServerRunning = !isServerRunning
                serverStatus = if (isServerRunning) "Działa na :8080" else "Zatrzymany"
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (isServerRunning) "Zatrzymaj serwer" else "Uruchom serwer")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Włącz Hotspot Wi-Fi na tym telefonie,\na następnie uruchom serwer.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
