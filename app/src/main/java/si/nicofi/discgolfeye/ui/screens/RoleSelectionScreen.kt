package si.nicofi.discgolfeye.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable
fun RoleSelectionScreen(
    onServerSelected: () -> Unit,
    onClientSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Default.SportsGolf,
            contentDescription = "Disc Golf",
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Disc Golf Eye",
            style = MaterialTheme.typography.headlineLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Wybierz rolę tego telefonu:",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(48.dp))

        Button(
            onClick = onServerSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Icon(Icons.Default.Videocam, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Jestem Kamerą", style = MaterialTheme.typography.titleMedium)
                Text("(Telefon w plecaku)", style = MaterialTheme.typography.bodySmall)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedButton(
            onClick = onClientSelected,
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
        ) {
            Icon(Icons.Default.Visibility, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Jestem Oglądaczem", style = MaterialTheme.typography.titleMedium)
                Text("(Telefon w ręce)", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
