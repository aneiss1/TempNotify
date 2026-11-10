package com.tempnotify.app

import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WeatherUpdateWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        if (!WeatherRepository.isEnabled(applicationContext)) {
            return Result.success()
        }

        val res = WeatherRepository.fetchCurrentWeather(applicationContext)
        return res.fold(
            onSuccess = { weather ->
                NotificationHelper.showNotification(applicationContext, weather)
                applicationContext.sendBroadcast(
                    Intent(MainActivity.ACTION_UI_UPDATED).setPackage(applicationContext.packageName)
                )
                Result.success()
            },
            onFailure = {
                // Keep cached notification visible even if network blips
                WeatherRepository.getCachedWeather(applicationContext)?.let { cached ->
                    NotificationHelper.showNotification(applicationContext, cached)
                }
                Result.retry()
            }
        )
    }

    companion object {
        private const val PERIODIC_WORK_NAME = "PeriodicWeatherUpdateWork"
        private const val IMMEDIATE_WORK_NAME = "ImmediateWeatherUpdateWork"

        fun schedulePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<WeatherUpdateWorker>(30, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun enqueueImmediate(context: Context) {
            val request = OneTimeWorkRequestBuilder<WeatherUpdateWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }
    }
}
