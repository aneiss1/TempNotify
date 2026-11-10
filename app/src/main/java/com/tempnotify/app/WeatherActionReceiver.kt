package com.tempnotify.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class WeatherActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (!WeatherRepository.isEnabled(context)) return

        when (intent?.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_REFRESH,
            ACTION_DISMISSED -> {
                // Immediately restore cached notification if swiped away, then refresh
                WeatherRepository.getCachedWeather(context)?.let { cached ->
                    NotificationHelper.showNotification(context, cached)
                }
                WeatherUpdateWorker.schedulePeriodic(context)
                WeatherUpdateWorker.enqueueImmediate(context)
                WeatherForegroundService.startOrUpdate(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH = "com.tempnotify.app.ACTION_REFRESH"
        const val ACTION_DISMISSED = "com.tempnotify.app.ACTION_DISMISSED"
    }
}
