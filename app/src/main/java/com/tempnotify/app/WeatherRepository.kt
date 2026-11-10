package com.tempnotify.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.math.roundToInt

data class WeatherData(
    val tempF: Int,
    val feelsLikeF: Int,
    val highF: Int,
    val lowF: Int,
    val conditionText: String,
    val cityName: String,
    val updatedTime: String,
    val latitude: Double,
    val longitude: Double
)

object WeatherRepository {

    private const val PREFS_NAME = "temp_notify_prefs"
    private const val KEY_LAT = "last_lat"
    private const val KEY_LON = "last_lon"
    private const val KEY_CITY = "last_city"
    private const val KEY_TEMP = "last_temp"
    private const val KEY_FEELS = "last_feels"
    private const val KEY_HIGH = "last_high"
    private const val KEY_LOW = "last_low"
    private const val KEY_COND = "last_cond"
    private const val KEY_UPDATED = "last_updated"
    private const val KEY_ENABLED = "notification_enabled"

    fun isEnabled(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_ENABLED, true)
    }

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_ENABLED, enabled)
            .apply()
    }

    fun getCachedWeather(context: Context): WeatherData? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (!prefs.contains(KEY_TEMP)) return null
        return WeatherData(
            tempF = prefs.getInt(KEY_TEMP, 70),
            feelsLikeF = prefs.getInt(KEY_FEELS, 70),
            highF = prefs.getInt(KEY_HIGH, 75),
            lowF = prefs.getInt(KEY_LOW, 60),
            conditionText = prefs.getString(KEY_COND, "Clear") ?: "Clear",
            cityName = prefs.getString(KEY_CITY, "Local") ?: "Local",
            updatedTime = prefs.getString(KEY_UPDATED, "--") ?: "--",
            latitude = Double.fromBits(prefs.getLong(KEY_LAT, (0.0).toRawBits())),
            longitude = Double.fromBits(prefs.getLong(KEY_LON, (0.0).toRawBits()))
        )
    }

    private fun saveWeather(context: Context, data: WeatherData) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putInt(KEY_TEMP, data.tempF)
            .putInt(KEY_FEELS, data.feelsLikeF)
            .putInt(KEY_HIGH, data.highF)
            .putInt(KEY_LOW, data.lowF)
            .putString(KEY_COND, data.conditionText)
            .putString(KEY_CITY, data.cityName)
            .putString(KEY_UPDATED, data.updatedTime)
            .putLong(KEY_LAT, data.latitude.toRawBits())
            .putLong(KEY_LON, data.longitude.toRawBits())
            .apply()
    }

    suspend fun fetchCurrentWeather(context: Context): Result<WeatherData> = withContext(Dispatchers.IO) {
        try {
            val coords = resolveCoordinates(context)
                ?: return@withContext Result.failure(IllegalStateException("Location unavailable. Make sure Location permission is granted and Location is turned on."))

            val (lat, lon) = coords
            val weatherUrl = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,apparent_temperature,weather_code" +
                "&daily=temperature_2m_max,temperature_2m_min" +
                "&temperature_unit=fahrenheit" +
                "&timezone=auto" +
                "&forecast_days=1"

            val responseJson = httpGet(weatherUrl)
            val root = JSONObject(responseJson)
            val current = root.getJSONObject("current")
            val daily = root.getJSONObject("daily")

            val tempF = current.getDouble("temperature_2m").roundToInt()
            val feelsLikeF = current.getDouble("apparent_temperature").roundToInt()
            val weatherCode = current.getInt("weather_code")

            val maxArray = daily.getJSONArray("temperature_2m_max")
            val minArray = daily.getJSONArray("temperature_2m_min")
            val highF = if (maxArray.length() > 0) maxArray.getDouble(0).roundToInt() else tempF
            val lowF = if (minArray.length() > 0) minArray.getDouble(0).roundToInt() else tempF

            val cityName = resolveCityName(context, lat, lon)
            val conditionText = mapWmoCode(weatherCode)
            val updatedTime = SimpleDateFormat("h:mm a", Locale.US).format(Date())

            val data = WeatherData(
                tempF = tempF,
                feelsLikeF = feelsLikeF,
                highF = highF,
                lowF = lowF,
                conditionText = conditionText,
                cityName = cityName,
                updatedTime = updatedTime,
                latitude = lat,
                longitude = lon
            )
            saveWeather(context, data)
            Result.success(data)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun resolveCoordinates(context: Context): Pair<Double, Double>? {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
            LocationManager.PASSIVE_PROVIDER
        )

        var bestLocation: Location? = null
        for (provider in providers) {
            try {
                if (locationManager.isProviderEnabled(provider)) {
                    val loc = locationManager.getLastKnownLocation(provider)
                    if (loc != null) {
                        if (bestLocation == null || loc.time > bestLocation!!.time) {
                            bestLocation = loc
                        }
                    }
                }
            } catch (_: SecurityException) {
            } catch (_: Exception) {
            }
        }

        // If lastKnownLocation is older than 20 minutes or null, request a quick fresh fix
        val twentyMinutesAgo = System.currentTimeMillis() - 20 * 60 * 1000L
        if (bestLocation == null || bestLocation.time < twentyMinutesAgo) {
            val fresh = requestSingleLocationUpdate(locationManager)
            if (fresh != null) {
                bestLocation = fresh
            }
        }

        if (bestLocation != null) {
            return Pair(bestLocation.latitude, bestLocation.longitude)
        }

        // Fallback to cached coordinates if available
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.contains(KEY_LAT) && prefs.contains(KEY_LON)) {
            val lat = Double.fromBits(prefs.getLong(KEY_LAT, 0L))
            val lon = Double.fromBits(prefs.getLong(KEY_LON, 0L))
            if (lat != 0.0 || lon != 0.0) {
                return Pair(lat, lon)
            }
        }

        return null
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestSingleLocationUpdate(locationManager: LocationManager): Location? {
        val activeProvider = when {
            runCatching { locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false) ->
                LocationManager.NETWORK_PROVIDER
            runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false) ->
                LocationManager.GPS_PROVIDER
            else -> return null
        }

        return withTimeoutOrNull(6000L) {
            suspendCancellableCoroutine { cont ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        locationManager.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }
                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    locationManager.requestLocationUpdates(
                        activeProvider,
                        0L,
                        0f,
                        listener,
                        Looper.getMainLooper()
                    )
                    cont.invokeOnCancellation {
                        locationManager.removeUpdates(listener)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    private fun resolveCityName(context: Context, lat: Double, lon: Double): String {
        // 1. Try built-in Android Geocoder first (fast and local on Pixel devices)
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val addresses = geocoder.getFromLocation(lat, lon, 1)
                if (!addresses.isNullOrEmpty()) {
                    val addr = addresses[0]
                    val city = addr.locality ?: addr.subAdminArea ?: addr.adminArea
                    if (!city.isNullOrBlank()) return city
                }
            }
        } catch (_: Exception) {
        }

        // 2. Fallback to free keyless BigDataCloud reverse geocode API
        try {
            val url = "https://api.bigdatacloud.net/data/reverse-geocode-client?latitude=$lat&longitude=$lon&localityLanguage=en"
            val json = JSONObject(httpGet(url))
            val city = json.optString("city").ifBlank {
                json.optString("locality").ifBlank {
                    json.optString("principalSubdivision")
                }
            }
            if (city.isNotBlank()) return city
        } catch (_: Exception) {
        }

        // 3. Fallback to cached city name
        val cached = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CITY, null)
        return if (!cached.isNullOrBlank()) cached else "Local Area"
    }

    private fun httpGet(urlString: String): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 8000
            readTimeout = 8000
            setRequestProperty("User-Agent", "TempNotify-Android/1.0")
        }
        try {
            val code = conn.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("HTTP $code from weather server")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun mapWmoCode(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Overcast"
        45, 48 -> "Foggy"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61 -> "Light Rain"
        63 -> "Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71 -> "Light Snow"
        73 -> "Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80, 81, 82 -> "Rain Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Heavy Thunderstorm"
        else -> "Clear"
    }
}
