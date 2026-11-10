package com.tempnotify.app

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Keeps the temperature notification strictly persistent in the status bar on Android 14+
 * and refreshes the weather every 30 minutes.
 */
class WeatherForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var loopJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForegroundWithCurrentOrPlaceholder()
        startPeriodicLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!WeatherRepository.isEnabled(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundWithCurrentOrPlaceholder()
        serviceScope.launch {
            val result = WeatherRepository.fetchCurrentWeather(applicationContext)
            result.onSuccess { data ->
                NotificationHelper.showNotification(applicationContext, data)
                sendBroadcast(Intent(MainActivity.ACTION_UI_UPDATED).setPackage(packageName))
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithCurrentOrPlaceholder() {
        val cached = WeatherRepository.getCachedWeather(this) ?: WeatherData(
            tempF = 72,
            feelsLikeF = 72,
            highF = 75,
            lowF = 60,
            conditionText = "Updating...",
            cityName = "Locating...",
            updatedTime = "Just now",
            latitude = 0.0,
            longitude = 0.0
        )
        val notification = NotificationHelper.buildNotification(this, cached)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NotificationHelper.NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NotificationHelper.NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
            // If started from background restriction where FGS isn't allowed, still post the notification directly
            NotificationHelper.showNotification(this, cached)
        }
    }

    private fun startPeriodicLoop() {
        loopJob?.cancel()
        loopJob = serviceScope.launch {
            while (isActive) {
                delay(30 * 60 * 1000L) // 30 minutes
                if (WeatherRepository.isEnabled(applicationContext)) {
                    val res = WeatherRepository.fetchCurrentWeather(applicationContext)
                    res.onSuccess { data ->
                        NotificationHelper.showNotification(applicationContext, data)
                        sendBroadcast(Intent(MainActivity.ACTION_UI_UPDATED).setPackage(packageName))
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun startOrUpdate(context: Context) {
            if (!WeatherRepository.isEnabled(context)) return
            try {
                val intent = Intent(context, WeatherForegroundService::class.java)
                ContextCompat.startForegroundService(context, intent)
            } catch (_: Exception) {
                // Fallback to WorkManager immediate job if OS blocks background FGS start
                WeatherUpdateWorker.enqueueImmediate(context)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, WeatherForegroundService::class.java))
            NotificationHelper.cancelNotification(context)
        }
    }
}
