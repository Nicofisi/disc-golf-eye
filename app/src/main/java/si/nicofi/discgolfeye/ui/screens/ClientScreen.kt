package si.nicofi.discgolfeye.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import si.nicofi.discgolfeye.client.DiscGolfClient

@Composable
fun ClientScreen(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connectionStatus by remember { mutableStateOf("Niepołączony") }
    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var serverMessage by remember { mutableStateOf("") }
    var gatewayIp by remember { mutableStateOf<String?>(null) }

    val client = remember { DiscGolfClient() }

    DisposableEffect(Unit) {
        onDispose { client.close() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "👁️ Tryb Oglądacza",
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
                    text = "Status połączenia:",
                    style = MaterialTheme.typography.titleSmall
                )
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                } else {
                    Text(
                        text = connectionStatus,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (isConnected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error
                    )
                }

                if (gatewayIp != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Serwer: $gatewayIp",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (serverMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = serverMessage,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                isLoading = true
                serverMessage = ""

                scope.launch {
                    // Pobierz IP bramy (gateway)
                    val ip = DiscGolfClient.getGatewayIp(context)
                    gatewayIp = ip

                    if (ip == null) {
                        connectionStatus = "Brak połączenia Wi-Fi"
                        isConnected = false
                        isLoading = false
                        return@launch
                    }

                    // Spróbuj połączyć się z serwerem
                    val result = client.ping(ip)

                    result.fold(
                        onSuccess = { response ->
                            connectionStatus = "Połączono!"
                            serverMessage = response.message
                            isConnected = true
                        },
                        onFailure = { error ->
                            connectionStatus = "Błąd połączenia"
                            serverMessage = error.message ?: "Nieznany błąd"
                            isConnected = false
                        }
                    )
                    isLoading = false
                }
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(if (isLoading) "Łączenie..." else "Połącz z kamerą")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Połącz się z Hotspotem telefonu-kamery,\na następnie kliknij przycisk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
