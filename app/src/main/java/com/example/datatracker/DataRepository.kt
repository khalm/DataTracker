package com.example.datatracker

import android.app.AppOpsManager
import android.app.usage.NetworkStatsManager
import android.content.Context
import android.net.NetworkCapabilities
import android.net.TrafficStats
import android.os.Process
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DataRepository(private val context: Context) {

    private val networkStatsManager = 
        context.getSystemService(Context.NETWORK_STATS_SERVICE) as NetworkStatsManager

    fun hasUsagePermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun getRealtimeSpeedFlow(): Flow<Long> = flow {
        var lastRxTx = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
        while (true) {
            delay(1000)
            val currentRxTx = TrafficStats.getMobileRxBytes() + TrafficStats.getMobileTxBytes()
            val diff = (currentRxTx - lastRxTx).coerceAtLeast(0L)
            lastRxTx = currentRxTx
            emit(diff)
        }
    }

    fun getMobileBytesBetween(startTimeEpochMs: Long, endTimeEpochMs: Long): Long {
        if (!hasUsagePermission()) return 0L
        return try {
            val bucket = networkStatsManager.querySummaryForDevice(
                NetworkCapabilities.TRANSPORT_CELLULAR,
                null,
                startTimeEpochMs,
                endTimeEpochMs
            )
            bucket.rxBytes + bucket.txBytes
        } catch (e: Exception) {
            0L
        }
    }
}
