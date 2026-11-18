package com.ingberny.mosaico

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

class WeatherDay(val name: String, val code: Int, val hi: Int, val lo: Int)

class WeatherData(
    val city: String,
    val temp: Int,
    val code: Int,
    val isDay: Boolean,
    val hi: Int,
    val lo: Int,
    val days: List<WeatherDay>,
) {
    val glyph: G get() = Glyphs.forWeather(code, isDay)
    val description: String get() = Weather.describe(code)

    fun toJson(): String = JSONObject().apply {
        put("city", city); put("temp", temp); put("code", code); put("isDay", isDay)
        put("hi", hi); put("lo", lo)
        put("days", JSONArray().apply {
            days.forEach { d -> put(JSONObject().put("n", d.name).put("c", d.code).put("h", d.hi).put("l", d.lo)) }
        })
    }.toString()

    companion object {
        fun fromJson(s: String?): WeatherData? = try {
            val o = JSONObject(s ?: "")
            val arr = o.getJSONArray("days")
            val days = (0 until arr.length()).map {
                val d = arr.getJSONObject(it)
                WeatherDay(d.getString("n"), d.getInt("c"), d.getInt("h"), d.getInt("l"))
            }
            WeatherData(o.getString("city"), o.getInt("temp"), o.getInt("code"), o.getBoolean("isDay"),
                o.getInt("hi"), o.getInt("lo"), days)
        } catch (e: Exception) { null }
    }
}

/** Clima con Open-Meteo (gratis, sin clave de API). */
object Weather {

    fun describe(code: Int): String = when (code) {
        0 -> "Despejado"
        1 -> "Mayormente despejado"
        2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Niebla"
        in 51..57 -> "Llovizna"
        in 61..67 -> "Lluvia"
        in 80..82 -> "Chubascos"
        in 71..77, 85, 86 -> "Nieve"
        in 95..99 -> "Tormenta"
        else -> "—"
    }

    class NeedsLocation : Exception()
    class NoFix : Exception()

    /** Bloqueante: llamar fuera del hilo principal. */
    fun fetch(ctx: Context, prefs: Prefs): WeatherData {
        var lat: Double
        var lon: Double
        var city: String
        if (prefs.city.isNotBlank()) {
            val q = URLEncoder.encode(prefs.city.trim(), "UTF-8")
            val geo = JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=1&language=es&format=json"))
            val r = geo.optJSONArray("results")?.optJSONObject(0) ?: throw Exception("Ciudad no encontrada")
            lat = r.getDouble("latitude"); lon = r.getDouble("longitude"); city = r.getString("name")
        } else {
            if (ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                throw NeedsLocation()
            val lm = ctx.getSystemService(LocationManager::class.java)
            var loc: Location? = lm.getProviders(true).mapNotNull {
                try { lm.getLastKnownLocation(it) } catch (e: SecurityException) { null }
            }.maxByOrNull { it.time }
            if (loc == null && Build.VERSION.SDK_INT >= 30) {
                val provider = when {
                    lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                    lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                    else -> null
                }
                if (provider != null) {
                    val latch = CountDownLatch(1)
                    val ex = Executors.newSingleThreadExecutor()
                    try {
                        lm.getCurrentLocation(provider, null, ex) { l -> loc = l; latch.countDown() }
                        latch.await(15, TimeUnit.SECONDS)
                    } catch (e: SecurityException) { } finally { ex.shutdown() }
                }
            }
            val fix = loc ?: throw NoFix()
            lat = fix.latitude; lon = fix.longitude
            city = try {
                @Suppress("DEPRECATION")
                val a = Geocoder(ctx, Locale.getDefault()).getFromLocation(lat, lon, 1)?.firstOrNull()
                a?.locality ?: a?.subAdminArea ?: a?.adminArea ?: ""
            } catch (e: Exception) { "" }
        }
        val unit = if (prefs.celsius) "" else "&temperature_unit=fahrenheit"
        val url = String.format(Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,weather_code,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min" +
                "&timezone=auto&forecast_days=4", lat, lon) + unit
        val o = JSONObject(get(url))
        val cur = o.getJSONObject("current")
        val daily = o.getJSONObject("daily")
        val times = daily.getJSONArray("time")
        val codes = daily.getJSONArray("weather_code")
        val maxs = daily.getJSONArray("temperature_2m_max")
        val mins = daily.getJSONArray("temperature_2m_min")
        val days = (1 until times.length()).map {
            val d = LocalDate.parse(times.getString(it))
            WeatherDay(
                d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()).trimEnd('.').capFirst(),
                codes.getInt(it), maxs.getDouble(it).roundToInt(), mins.getDouble(it).roundToInt()
            )
        }
        return WeatherData(
            city = city,
            temp = cur.getDouble("temperature_2m").roundToInt(),
            code = cur.getInt("weather_code"),
            isDay = cur.optInt("is_day", 1) == 1,
            hi = maxs.getDouble(0).roundToInt(),
            lo = mins.getDouble(0).roundToInt(),
            days = days,
        )
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000; c.readTimeout = 10000
        try {
            if (c.responseCode != 200) throw Exception("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
