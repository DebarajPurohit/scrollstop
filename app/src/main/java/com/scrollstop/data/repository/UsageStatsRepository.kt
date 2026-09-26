package com.scrollstop.data.repository

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import android.os.Process
import com.scrollstop.data.time.SystemTimeProvider
import com.scrollstop.data.time.TimeProvider

/**
 * Repository interface for detecting Usage Access permission and querying today's application usage stats.
 */
interface UsageStatsRepository {
    fun hasUsagePermission(): Boolean
    fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>>
}

/**
 * Android implementation of [UsageStatsRepository] backed by [UsageStatsManager] and [AppOpsManager].
 */
class AndroidUsageStatsRepository(
    private val context: Context,
    private val timeProvider: TimeProvider = SystemTimeProvider()
) : UsageStatsRepository {

    override fun hasUsagePermission(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
                ?: return false

            val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                @Suppress("DEPRECATION")
                appOps.unsafeCheckOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            } else {
                @Suppress("DEPRECATION")
                appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
            }
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    override fun getTodayUsageForPackages(packageNames: Set<String>): Result<Map<String, Long>> {
        if (!hasUsagePermission()) {
            return Result.failure(SecurityException("Usage Access permission not granted"))
        }

        return try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
                ?: return Result.failure(IllegalStateException("UsageStatsManager service unavailable"))

            val startTime = timeProvider.getStartOfDayMillis()
            val endTime = timeProvider.currentTimeMillis()

            val aggregatedStats = usageStatsManager.queryAndAggregateUsageStats(startTime, endTime)
                ?: emptyMap()

            val resultMap = packageNames.associateWith { pkg ->
                aggregatedStats[pkg]?.totalTimeInForeground ?: 0L
            }

            Result.success(resultMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
