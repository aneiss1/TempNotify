package com.tempnotify.app

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MainActivity : Activity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private lateinit var tempText: TextView
    private lateinit var feelsLikeText: TextView
    private lateinit var conditionCityText: TextView
    private lateinit var highLowText: TextView
    private lateinit var statusText: TextView
    private lateinit var enableSwitch: Switch

    private val uiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            renderCachedWeather()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        setupUi()
        renderCachedWeather()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter(ACTION_UI_UPDATED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(uiReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(uiReceiver, filter)
        }
        renderCachedWeather()
    }

    override fun onPause() {
        super.onPause()
        runCatching { unregisterReceiver(uiReceiver) }
    }

    override fun onDestroy() {
        uiScope.cancel()
        super.onDestroy()
    }

    private fun setupUi() {
        val density = resources.displayMetrics.density
        fun dp(v: Int) = (v * density).toInt()

        val rootScroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            isFillViewport = true
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(48), dp(24), dp(32))
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val headerTitle = TextView(this).apply {
            text = "TempNotify"
            textSize = 26f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }

        val subtitle = TextView(this).apply {
            text = "Persistent Status Bar Temperature (°F)"
            textSize = 14f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(4), 0, dp(24))
        }

        // Weather Card
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(28), dp(24), dp(28))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(24).toFloat()
            }
        }

        conditionCityText = TextView(this).apply {
            text = "Detecting Location..."
            textSize = 18f
            setTextColor(Color.parseColor("#E2E8F0"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        tempText = TextView(this).apply {
            text = "--°F"
            textSize = 64f
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-light", Typeface.BOLD)
            setPadding(0, dp(8), 0, dp(4))
        }

        feelsLikeText = TextView(this).apply {
            text = "Feels like --°F"
            textSize = 20f
            setTextColor(Color.parseColor("#38BDF8"))
            gravity = Gravity.CENTER
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }

        highLowText = TextView(this).apply {
            text = "High: --°  •  Low: --°"
            textSize = 15f
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        }

        statusText = TextView(this).apply {
            text = "Tap Refresh below to update"
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
        }

        card.addView(conditionCityText)
        card.addView(tempText)
        card.addView(feelsLikeText)
        card.addView(highLowText)
        card.addView(statusText)

        // Toggle Row
        val toggleCard = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(16), dp(20), dp(16))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E293B"))
                cornerRadius = dp(16).toFloat()
            }
        }

        val toggleLabel = TextView(this).apply {
            text = "Persistent Status Bar Notification"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        enableSwitch = Switch(this).apply {
            isChecked = WeatherRepository.isEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, isChecked ->
                WeatherRepository.setEnabled(this@MainActivity, isChecked)
                if (isChecked) {
                    refreshNow()
                } else {
                    WeatherForegroundService.stop(this@MainActivity)
                    statusText.text = "Persistent notification paused"
                }
            }
        }

        toggleCard.addView(toggleLabel)
        toggleCard.addView(enableSwitch)

        val refreshButton = Button(this).apply {
            text = "Refresh Weather Now"
            textSize = 16f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#2563EB"))
                cornerRadius = dp(16).toFloat()
            }
            setPadding(dp(20), dp(14), dp(20), dp(14))
            setOnClickListener {
                checkAndRequestPermissions()
                refreshNow()
            }
        }

        val pixelWeatherNote = TextView(this).apply {
            text = "Tip: Tapping the notification in your shade opens the Pixel Weather app directly."
            textSize = 13f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(20), dp(12), 0)
        }

        container.addView(headerTitle)
        container.addView(subtitle)
        container.addView(
            card,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        )
        container.addView(
            toggleCard,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(16)
            }
        )
        container.addView(
            refreshButton,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        container.addView(pixelWeatherNote)

        rootScroll.addView(container)
        setContentView(rootScroll)
    }

    private fun renderCachedWeather() {
        val cached = WeatherRepository.getCachedWeather(this) ?: return
        tempText.text = "${cached.tempF}°F"
        feelsLikeText.text = "Feels like ${cached.feelsLikeF}°F"
        conditionCityText.text = "${cached.conditionText} • ${cached.cityName}"
        highLowText.text = "High: ${cached.highF}°F  •  Low: ${cached.lowF}°F"
        statusText.text = "Last updated at ${cached.updatedTime}"
    }

    private fun checkAndRequestPermissions() {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (needed.isNotEmpty()) {
            requestPermissions(needed.toTypedArray(), 100)
        } else {
            startServicesAndRefresh()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        startServicesAndRefresh()
    }

    private fun startServicesAndRefresh() {
        WeatherUpdateWorker.schedulePeriodic(this)
        WeatherForegroundService.startOrUpdate(this)
        refreshNow()
    }

    private fun refreshNow() {
        statusText.text = "Fetching local temperature..."
        uiScope.launch {
            val result = WeatherRepository.fetchCurrentWeather(applicationContext)
            result.fold(
                onSuccess = { data ->
                    NotificationHelper.showNotification(applicationContext, data)
                    WeatherForegroundService.startOrUpdate(applicationContext)
                    renderCachedWeather()
                },
                onFailure = { err ->
                    statusText.text = err.message ?: "Could not fetch weather"
                }
            )
        }
    }

    companion object {
        const val ACTION_UI_UPDATED = "com.tempnotify.app.ACTION_UI_UPDATED"
    }
}
