package si.nicofi.discgolfeye.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ClientScreen(
    modifier: Modifier = Modifier
) {
    var connectionStatus by remember { mutableStateOf("Niepołączony") }
    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

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
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                // TODO: Połącz z serwerem
                isLoading = true
            },
            enabled = !isLoading,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text("Połącz z kamerą")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Połącz się z Hotspotem telefonu-kamery,\na następnie kliknij przycisk.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
