package com.tempnotify.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {

    const val CHANNEL_ID = "status_bar_temp_channel_v2"
    private const val OLD_CHANNEL_ID = "persistent_temperature_channel"
    const val NOTIFICATION_ID = 1001
    private const val PIXEL_WEATHER_PACKAGE = "com.google.android.apps.weather"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Remove old low-importance channel so Android doesn't demote to "Silent" section
        runCatching { manager.deleteNotificationChannel(OLD_CHANNEL_ID) }

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Status Bar Temperature",
            NotificationManager.IMPORTANCE_DEFAULT // Required on Pixel so the icon shows in the top bar next to the time!
        ).apply {
            description = "Shows the current temperature icon in the top status bar next to the clock"
            setSound(null, null) // Silent audio, but Default importance so status bar icon is always shown
            setShowBadge(false)
            enableLights(false)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        manager.createNotificationChannel(channel)
    }

    fun buildNotification(context: Context, weather: WeatherData): Notification {
        ensureChannel(context)

        val smallIcon = TemperatureIconRenderer.createTemperatureIcon(weather.tempF)

        val title = "${weather.tempF}°F • ${weather.conditionText} · ${weather.cityName}"
        val summaryLine = "Feels like ${weather.feelsLikeF}°F  •  H: ${weather.highF}°  L: ${weather.lowF}°"
        val expandedText = "Feels like ${weather.feelsLikeF}°F\n" +
            "Today's High: ${weather.highF}°F  •  Low: ${weather.lowF}°F\n" +
            "Updated at ${weather.updatedTime} (Tap to open Weather)"

        val contentIntent = createTapPendingIntent(context)

        val refreshIntent = Intent(context, WeatherActionReceiver::class.java).apply {
            action = WeatherActionReceiver.ACTION_REFRESH
        }
        val refreshPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            refreshIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissedIntent = Intent(context, WeatherActionReceiver::class.java).apply {
            action = WeatherActionReceiver.ACTION_DISMISSED
        }
        val dismissedPendingIntent = PendingIntent.getBroadcast(
            context,
            2,
            dismissedIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val settingsIntent = Intent(context, MainActivity::class.java)
        val settingsPendingIntent = PendingIntent.getActivity(
            context,
            3,
            settingsIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(smallIcon)
            .setContentTitle(title)
            .setContentText(summaryLine)
            .setSubText("Feels like ${weather.feelsLikeF}°F")
            .setStyle(NotificationCompat.BigTextStyle().bigText(expandedText))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSound(null)
            .setVibrate(null)
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .setDeleteIntent(dismissedPendingIntent)
            .addAction(0, "Refresh", refreshPendingIntent)
            .addAction(0, "App Settings", settingsPendingIntent)
            .build()
    }

    fun showNotification(context: Context, weather: WeatherData) {
        if (!WeatherRepository.isEnabled(context)) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = buildNotification(context, weather)
        manager.notify(NOTIFICATION_ID, notification)
    }

    fun cancelNotification(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.cancel(NOTIFICATION_ID)
    }

    private fun createTapPendingIntent(context: Context): PendingIntent {
        val pm = context.packageManager
        val pixelWeatherIntent = pm.getLaunchIntentForPackage(PIXEL_WEATHER_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        val targetIntent = pixelWeatherIntent ?: Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }

        return PendingIntent.getActivity(
            context,
            0,
            targetIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}
