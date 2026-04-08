package com.example.kidshield.services

import android.app.Service
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.example.kidshield.network.BackendManager
import java.text.SimpleDateFormat
import android.os.Build
import java.util.Date
import java.util.Locale

class ScreenTimeService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var childId: Int = -1
    private val POLLING_INTERVAL = 120000L // Reduced to 2 minutes to save battery
    
    private val CHANNEL_ID = "ScreenTimeChannel"
    private val NOTIFICATION_ID = 54321
    
    // Throttling state to avoid spamming the parent
    private var lastLimitAlertUsage = 0
    private var lastBedtimeAlertTime = 0L
    private var lastSchoolAlertTime = 0L

    private val screenUsageRunnable = object : Runnable {
        override fun run() {
            if (childId != -1) {
                uploadRealScreenUsage()
            }
            handler.postDelayed(this, POLLING_INTERVAL)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ScreenTimeService", "Service Created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val session = com.example.kidshield.utils.SessionManager.getInstance(this)
        childId = session.childId
        
        if (childId == -1) {
            Log.e("ScreenTimeService", "No active child ID. Stopping.")
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        val notification = androidx.core.app.NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("KidShield Screen Monitor")
            .setContentText("Monitoring screen time limits...")
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_MIN)
            .build()
            
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e("ScreenTimeService", "Foreground start failed: ${e.message}")
        }
        
        handler.removeCallbacks(screenUsageRunnable)
        handler.post(screenUsageRunnable)

        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                CHANNEL_ID, "Screen Time Monitoring",
                android.app.NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(android.app.NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    private fun uploadRealScreenUsage() {
        val usageStatsManager = getSystemService(android.content.Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val endTime = System.currentTimeMillis()
        
        val calendar = java.util.Calendar.getInstance()
        calendar.set(java.util.Calendar.HOUR_OF_DAY, 0)
        calendar.set(java.util.Calendar.MINUTE, 0)
        calendar.set(java.util.Calendar.SECOND, 0)
        calendar.set(java.util.Calendar.MILLISECOND, 0)
        val startTime = calendar.timeInMillis // Precisely since midnight
        
        Log.d("ScreenTimeService", "Querying usage from $startTime to $endTime")
        
        val usageStats = usageStatsManager.queryUsageStats(
            android.app.usage.UsageStatsManager.INTERVAL_DAILY,
            startTime,
            endTime
        )

        if (usageStats != null && usageStats.isNotEmpty()) {
            var calculatedTotalMinutes = 0
            val todayDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            val pm = packageManager

            // Manually aggregate to be safe across Android versions
            val aggregatedMap = mutableMapOf<String, Long>()
            for (stats in usageStats) {
                val current = aggregatedMap[stats.packageName] ?: 0L
                aggregatedMap[stats.packageName] = current + stats.totalTimeInForeground
            }

            // 1. Prepare Bulk Sync — use DISPLAY LABEL not package name
            //    AppBlockerService.getForegroundAppName() also returns the display label,
            //    so both sides must agree on the same name format.
            val usageList = mutableListOf<com.example.kidshield.models.AppUsage>()
            for (entry in aggregatedMap) {
                val pkgName = entry.key
                val usageTimeMinutes = (entry.value / (1000 * 60)).toInt()

                if (usageTimeMinutes > 0) {
                    calculatedTotalMinutes += usageTimeMinutes

                    // Resolve human-readable label; fall back to package name on failure
                    val displayLabel: String = try {
                        val appInfo = pm.getApplicationInfo(pkgName, 0)
                        pm.getApplicationLabel(appInfo).toString()
                    } catch (ex: Exception) {
                        pkgName  // fallback keeps data even if label lookup fails
                    }

                    usageList.add(com.example.kidshield.models.AppUsage(displayLabel, usageTimeMinutes))
                }
            }


            // 2. Perform Single Bulk Sync Call
            if (usageList.isNotEmpty()) {
                BackendManager.bulkUpdateScreenUsage(
                    childId = childId,
                    date = todayDate,
                    usages = usageList,
                    callback = object : BackendManager.ApiCallback<String> {
                        override fun onSuccess(result: String) {
                            Log.d("ScreenTimeService", "Bulk Usage Sync Successful: ${usageList.size} apps")
                        }
                        override fun onError(error: String) {
                            Log.e("ScreenTimeService", "Bulk Sync Failed: $error")
                        }
                    }
                )
            }
            
            Log.d("ScreenTimeService", "Total Daily Usage Calculated: $calculatedTotalMinutes minutes")
            
            // 3. Trigger alerts based on total minutes
            checkLimitsAndTriggerAlert(calculatedTotalMinutes)
        } else {
            Log.w("ScreenTimeService", "No usage list returned. Check Usage Access permissions.")
        }
    }

    private fun checkLimitsAndTriggerAlert(currentUsageMinutes: Int) {
        BackendManager.getScreenTimeLimits(childId, object : BackendManager.ApiCallback<List<com.example.kidshield.models.ScreenTimeLimitResponse>> {
            override fun onSuccess(limits: List<com.example.kidshield.models.ScreenTimeLimitResponse>) {
                val overallLimit = limits.find { it.appName.isNullOrEmpty() }
                if (overallLimit != null) {
                    val currentTime = System.currentTimeMillis()

                    // 1. Check Daily Limit with 60min Throttling
                    if (currentUsageMinutes > overallLimit.limitMinutes) {
                        // Trigger if it's the first time or if an hour (60 mins) has passed since last alert
                        if (lastLimitAlertUsage == 0 || currentUsageMinutes >= lastLimitAlertUsage + 60) {
                            Log.d("ScreenTimeService", "Limit Exceeded! Usage: $currentUsageMinutes, Limit: ${overallLimit.limitMinutes}")
                            lastLimitAlertUsage = currentUsageMinutes
                            BackendManager.sendAlert(
                                childId, 
                                "screen_time", 
                                "Child has exceeded their daily screen time limit (${currentUsageMinutes}m / ${overallLimit.limitMinutes}m)",
                                object : BackendManager.ApiCallback<String> {
                                    override fun onSuccess(res: String) {}
                                    override fun onError(err: String) {}
                                }
                            )
                        }
                    } else {
                        // Reset when back under limit
                        lastLimitAlertUsage = 0
                    }

                    // 2. Check Bedtime Mode (1 Hour Throttling)
                    if (overallLimit.bedtimeEnabled && overallLimit.bedtimeStart != null && overallLimit.bedtimeEnd != null) {
                        if (isTimeInRange(overallLimit.bedtimeStart, overallLimit.bedtimeEnd)) {
                            if (currentTime >= lastBedtimeAlertTime + 3600000L) { // 1 hour in ms
                                Log.d("ScreenTimeService", "Bedtime Mode Active!")
                                lastBedtimeAlertTime = currentTime
                                BackendManager.sendAlert(
                                    childId,
                                    "screen_time",
                                    "Child is using the device during Bedtime Mode (${overallLimit.bedtimeStart} - ${overallLimit.bedtimeEnd})",
                                    object : BackendManager.ApiCallback<String> {
                                        override fun onSuccess(res: String) {}
                                        override fun onError(err: String) {}
                                    }
                                )
                            }
                        } else {
                            lastBedtimeAlertTime = 0
                        }
                    }

                    // 3. Check School Hours (1 Hour Throttling)
                    if (overallLimit.schoolHoursEnabled && overallLimit.schoolStart != null && overallLimit.schoolEnd != null) {
                        val calendar = java.util.Calendar.getInstance()
                        val day = calendar.get(java.util.Calendar.DAY_OF_WEEK)
                        val isWeekday = day != java.util.Calendar.SATURDAY && day != java.util.Calendar.SUNDAY
                        
                        if (isWeekday && isTimeInRange(overallLimit.schoolStart, overallLimit.schoolEnd)) {
                            if (currentTime >= lastSchoolAlertTime + 3600000L) {
                                Log.d("ScreenTimeService", "School Hours Block Active!")
                                lastSchoolAlertTime = currentTime
                                BackendManager.sendAlert(
                                    childId,
                                    "school_hours",
                                    "Child is using the device during School Hours (${overallLimit.schoolStart} - ${overallLimit.schoolEnd})",
                                    object : BackendManager.ApiCallback<String> {
                                        override fun onSuccess(res: String) {}
                                        override fun onError(err: String) {}
                                    }
                                )
                            }
                        } else {
                            lastSchoolAlertTime = 0
                        }
                    }
                }
            }
            override fun onError(error: String) {
                Log.e("ScreenTimeService", "Failed to get limits: $error")
            }
        })
    }

    private fun isTimeInRange(startTime: String, endTime: String): Boolean {
        try {
            val now = java.util.Calendar.getInstance()
            val currentMins = now.get(java.util.Calendar.HOUR_OF_DAY) * 60 + now.get(java.util.Calendar.MINUTE)

            val s = startTime.split(":")
            val startMins = s[0].toInt() * 60 + s[1].toInt()

            val e = endTime.split(":")
            val endMins = e[0].toInt() * 60 + e[1].toInt()

            return if (startMins <= endMins) {
                currentMins in startMins..endMins
            } else {
                // Time range wraps around midnight (e.g., 9 PM to 7 AM)
                currentMins >= startMins || currentMins <= endMins
            }
        } catch (e: Exception) {
            return false
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(screenUsageRunnable)
        Log.d("ScreenTimeService", "Service Destroyed")
    }
}
