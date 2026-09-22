package com.example.datatracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.*

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
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(repository.hasUsagePermission()) }
    var currentSpeedBps by remember { mutableLongStateOf(0L) }
    
    val minuteBuffer = remember { ArrayDeque<Long>(60) }
    var avgSpeedPerMinuteBps by remember { mutableLongStateOf(0L) }

    // Starttidspunkt for målingen (standard: 24 timer tilbake)
    var historicalStartTime by remember { 
        mutableLongStateOf(System.currentTimeMillis() - 24 * 3600 * 1000) 
    }
    var totalHistoricalBytes by remember { mutableLongStateOf(0L) }

    val dateFormatter = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()) }

    // Lytter på sanntidsfart
    LaunchedEffect(Unit) {
        repository.getRealtimeSpeedFlow().collect { bytesThisSec ->
            currentSpeedBps = bytesThisSec

            if (minuteBuffer.size >= 60) minuteBuffer.removeFirst()
            minuteBuffer.addLast(bytesThisSec)
            avgSpeedPerMinuteBps = minuteBuffer.sum() / minuteBuffer.size
        }
    }

    // Henter databruk fra valgt starttidspunkt frem til nåværende øyeblikk
    LaunchedEffect(hasPermission, historicalStartTime) {
        if (hasPermission) {
            totalHistoricalBytes = repository.getMobileBytesBetween(
                historicalStartTime,
                System.currentTimeMillis()
            )
        }
    }

    // Funksjon som åpner dato- og deretter klokkeslettvelger
    fun pickDateTime() {
        val currentCalendar = Calendar.getInstance().apply { timeInMillis = historicalStartTime }
        
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                val chosenCalendar = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                }
                
                TimePickerDialog(
                    context,
                    { _, hourOfDay, minute ->
                        chosenCalendar.set(Calendar.HOUR_OF_DAY, hourOfDay)
                        chosenCalendar.set(Calendar.MINUTE, minute)
                        chosenCalendar.set(Calendar.SECOND, 0)
                        
                        historicalStartTime = chosenCalendar.timeInMillis
                    },
                    currentCalendar.get(Calendar.HOUR_OF_DAY),
                    currentCalendar.get(Calendar.MINUTE),
                    true // 24-timers format
                ).show()
            },
            currentCalendar.get(Calendar.YEAR),
            currentCalendar.get(Calendar.MONTH),
            currentCalendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Mobildata Monitor", style = MaterialTheme.typography.headlineMedium)

        if (!hasPermission) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Appen trenger bruksadgang for å telle mobildata.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onRequestPermission) {
                        Text("Gi tilgang i innstillinger")
                    }
                }
            }
        }

        // Sanntid og snitt
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Sanntidsforbruk", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = "${formatBytes(currentSpeedBps)}/s",
                    style = MaterialTheme.typography.displaySmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Gjennomsnitt siste minutt: ${formatBytes(avgSpeedPerMinuteBps)}/s",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        // Egendefinert tidsrom
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Forbruk i valgt periode", style = MaterialTheme.typography.labelLarge)
                Text(
                    text = formatBytes(totalHistoricalBytes),
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Målt fra: ${dateFormatter.format(Date(historicalStartTime))}",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(Modifier.height(14.dp))
                
                Button(
                    onClick = { pickDateTime() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Velg dato og klokkeslett")
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { historicalStartTime = System.currentTimeMillis() - 3600 * 1000 }
                    ) {
                        Text("Siste time")
                    }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        onClick = { historicalStartTime = System.currentTimeMillis() - 24 * 3600 * 1000 }
                    ) {
                        Text("Siste 24t")
                    }
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
