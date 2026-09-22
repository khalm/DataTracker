package com.example.datatracker

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.util.ArrayDeque

class MainActivity : ComponentActivity() {

    private val repository by lazy { DataRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TrackerScreen(
                        repository = repository,
                        onRequestPermission = {
                            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun TrackerScreen(
    repository: DataRepository,
    onRequestPermission: () -> Unit
) {
    var hasPermission by remember { mutableStateOf(repository.hasUsagePermission()) }
    var currentSpeedBps by remember { mutableLongStateOf(0L) }
    
    val minuteBuffer = remember { ArrayDeque<Long>(60) }
    var avgSpeedPerMinuteBps by remember { mutableLongStateOf(0L) }

    var historicalStartTime by remember { 
        mutableLongStateOf(System.currentTimeMillis() - 24 * 3600 * 1000) 
    }
    var totalHistoricalBytes by remember { mutableLongStateOf(0L) }

    LaunchedEffect(Unit) {
        repository.getRealtimeSpeedFlow().collect { bytesThisSec ->
            currentSpeedBps = bytesThisSec

            if (minuteBuffer.size >= 60) minuteBuffer.removeFirst()
            minuteBuffer.addLast(bytesThisSec)
            avgSpeedPerMinuteBps = minuteBuffer.sum() / minuteBuffer.size
        }
    }

    LaunchedEffect(hasPermission, historicalStartTime) {
        if (hasPermission) {
            totalHistoricalBytes = repository.getMobileBytesBetween(
                historicalStartTime,
                System.currentTimeMillis()
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mobildata Monitor", style = MaterialTheme.typography.headlineMedium)

        if (!hasPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Appen trenger tilgang for å lese dataforbruk over tid.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Gi tilgang i innstillinger")
                    }
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sanntidsforbruk", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${formatBytes(currentSpeedBps)}/s",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Snitt siste minutt: ${formatBytes(avgSpeedPerMinuteBps)}/s",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Forbruk i tidsrom", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatBytes(totalHistoricalBytes),
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        historicalStartTime = System.currentTimeMillis() - 3600 * 1000
                    }) { Text("Siste time") }

                    Button(onClick = {
                        historicalStartTime = System.currentTimeMillis() - 24 * 3600 * 1000
                    }) { Text("Siste 24t") }
                }
            }
        }
    }
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, 4)
    val df = DecimalFormat("#,##0.##")
    return "${df.format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}
