package com.huyluong.vnedmontonwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class DualTimeWeatherWidget : AppWidgetProvider() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WeatherSyncWorker.schedule(context)
        syncAndUpdateAll(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        WeatherSyncWorker.schedule(context)
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id, forceFetch = true)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId, forceFetch = false)
    }

    companion object {
        const val PREFS_NAME = "ios_glass_prefs"
        const val KEY_TRANSPARENCY = "transparency_pct"

        private const val KEY_VN_TEMP = "vn_temp"
        private const val KEY_VN_CODE = "vn_code"
        private const val KEY_VN_HUM = "vn_hum"
        private const val KEY_VN_WIND = "vn_wind"

        private const val KEY_EDM_TEMP = "edm_temp"
        private const val KEY_EDM_CODE = "edm_code"
        private const val KEY_EDM_HUM = "edm_hum"
        private const val KEY_EDM_WIND = "edm_wind"
        private const val KEY_LAST_SYNC = "last_sync_str"

        data class WeatherInfo(val temp: Int, val code: Int, val humidity: Int, val wind: Int, val isLive: Boolean)

        fun getSavedTransparency(context: Context): Int {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getInt(KEY_TRANSPARENCY, 60)
        }

        fun saveTransparency(context: Context, pct: Int) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putInt(KEY_TRANSPARENCY, pct.coerceIn(0, 100)).apply()
        }

        fun getCachedWeather(context: Context): Pair<WeatherInfo, WeatherInfo> {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val vn = WeatherInfo(
                temp = prefs.getInt(KEY_VN_TEMP, 29),
                code = prefs.getInt(KEY_VN_CODE, 1),
                humidity = prefs.getInt(KEY_VN_HUM, 70),
                wind = prefs.getInt(KEY_VN_WIND, 8),
                isLive = prefs.contains(KEY_VN_TEMP)
            )
            val edm = WeatherInfo(
                temp = prefs.getInt(KEY_EDM_TEMP, -5),
                code = prefs.getInt(KEY_EDM_CODE, 71),
                humidity = prefs.getInt(KEY_EDM_HUM, 80),
                wind = prefs.getInt(KEY_EDM_WIND, 14),
                isLive = prefs.contains(KEY_EDM_TEMP)
            )
            return Pair(vn, edm)
        }

        fun saveWeatherCache(context: Context, vn: WeatherInfo, edm: WeatherInfo) {
            val nowVn = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                .format(DateTimeFormatter.ofPattern("HH:mm dd/MM"))
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putInt(KEY_VN_TEMP, vn.temp)
                .putInt(KEY_VN_CODE, vn.code)
                .putInt(KEY_VN_HUM, vn.humidity)
                .putInt(KEY_VN_WIND, vn.wind)
                .putInt(KEY_EDM_TEMP, edm.temp)
                .putInt(KEY_EDM_CODE, edm.code)
                .putInt(KEY_EDM_HUM, edm.humidity)
                .putInt(KEY_EDM_WIND, edm.wind)
                .putString(KEY_LAST_SYNC, nowVn)
                .apply()
        }

        fun getLastSyncText(context: Context): String {
            return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(KEY_LAST_SYNC, "Chưa đồng bộ") ?: "Chưa đồng bộ"
        }

        fun syncAndUpdateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DualTimeWeatherWidget::class.java))
            CoroutineScope(Dispatchers.IO).launch {
                val (vnLive, edmLive) = fetchBothLive(context)
                for (id in ids) {
                    val options = manager.getAppWidgetOptions(id)
                    val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
                    val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 130)
                    val views = buildRemoteViews(context, w, h, vnLive, edmLive)
                    manager.updateAppWidget(id, views)
                }
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int, forceFetch: Boolean) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 130)

            val (cachedVn, cachedEdm) = getCachedWeather(context)
            manager.updateAppWidget(widgetId, buildRemoteViews(context, minWidth, minHeight, cachedVn, cachedEdm))

            if (forceFetch) {
                CoroutineScope(Dispatchers.IO).launch {
                    val (vnLive, edmLive) = fetchBothLive(context)
                    val views = buildRemoteViews(context, minWidth, minHeight, vnLive, edmLive)
                    manager.updateAppWidget(widgetId, views)
                }
            }
        }

        suspend fun fetchBothLive(context: Context): Pair<WeatherInfo, WeatherInfo> {
            val (cachedVn, cachedEdm) = getCachedWeather(context)
            val vn = fetchWeather(10.8231, 106.6297, cachedVn)
            val edm = fetchWeather(53.5461, -113.4938, cachedEdm)
            if (vn.isLive || edm.isLive) {
                saveWeatherCache(context, vn, edm)
            }
            return Pair(vn, edm)
        }

        fun buildRemoteViews(
            context: Context,
            widthDp: Int,
            heightDp: Int,
            vnWeather: WeatherInfo,
            edmWeather: WeatherInfo
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_dual_clock)

            val isCompact = widthDp < 240 || heightDp < 115
            val isTall = heightDp >= 180

            val clockSize = when {
                isCompact -> 24f
                isTall -> 40f
                else -> 33f
            }
            val tempSize = when {
                isCompact -> 15f
                isTall -> 23f
                else -> 19f
            }
            val metaSize = if (isCompact) 9.5f else if (isTall) 12f else 10.5f

            views.setTextViewTextSize(R.id.clock_vn, TypedValue.COMPLEX_UNIT_SP, clockSize)
            views.setTextViewTextSize(R.id.clock_edm, TypedValue.COMPLEX_UNIT_SP, clockSize)
            views.setTextViewTextSize(R.id.tv_vn_temp, TypedValue.COMPLEX_UNIT_SP, tempSize)
            views.setTextViewTextSize(R.id.tv_edm_temp, TypedValue.COMPLEX_UNIT_SP, tempSize)
            views.setTextViewTextSize(R.id.tv_vn_solar, TypedValue.COMPLEX_UNIT_SP, metaSize)
            views.setTextViewTextSize(R.id.tv_edm_solar, TypedValue.COMPLEX_UNIT_SP, metaSize)

            val descVisibility = if (heightDp < 105) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.tv_vn_weather_desc, descVisibility)
            views.setViewVisibility(R.id.tv_edm_weather_desc, descVisibility)
            views.setViewVisibility(R.id.tv_vn_offset, descVisibility)
            views.setViewVisibility(R.id.tv_edm_offset, descVisibility)

            val vnNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            val edmNow = ZonedDateTime.now(ZoneId.of("America/Edmonton"))
            val diffHours = ChronoUnit.HOURS.between(vnNow.toLocalDateTime(), edmNow.toLocalDateTime())
            val edmDayLabel = when {
                edmNow.toLocalDate().isBefore(vnNow.toLocalDate()) -> "Hôm qua"
                edmNow.toLocalDate().isAfter(vnNow.toLocalDate()) -> "Ngày mai"
                else -> "Hôm nay"
            }

            val transparencyPct = getSavedTransparency(context)
            val panelAlpha = (255 - (transparencyPct * 2.17f)).toInt().coerceIn(38, 255)
            val shellAlpha = (panelAlpha * 0.65f).toInt().coerceIn(24, 180)

            views.setInt(R.id.widget_root, "setBackgroundResource", R.drawable.bg_ios_glass_shell)
            views.setInt(R.id.widget_root, "setAlpha", shellAlpha)

            val vnLunar = LunarCalendar.convertSolar2Lunar(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year, 7.0)
            views.setTextViewText(R.id.tv_vn_offset, "Hôm nay • +${-diffHours}g")
            views.setTextViewText(R.id.tv_vn_solar, formatSolarDate(vnNow))
            views.setTextViewText(R.id.tv_vn_lunar, vnLunar.formatShort())
            views.setTextViewText(R.id.tv_vn_temp, "${weatherEmoji(vnWeather.code, vnNow.hour)} ${vnWeather.temp}°")
            views.setTextViewText(R.id.tv_vn_weather_desc, "${weatherLabel(vnWeather.code)} • Ẩm ${vnWeather.humidity}%")
            views.setInt(R.id.panel_vn, "setBackgroundResource", pickIosBackground(vnWeather.code, vnNow.hour))
            views.setInt(R.id.panel_vn, "setAlpha", panelAlpha)

            val edmLunar = LunarCalendar.convertSolar2Lunar(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year, 7.0)
            views.setTextViewText(R.id.tv_edm_offset, "$edmDayLabel • ${diffHours}g")
            views.setTextViewText(R.id.tv_edm_solar, formatSolarDate(edmNow))
            views.setTextViewText(R.id.tv_edm_lunar, edmLunar.formatShort())
            views.setTextViewText(R.id.tv_edm_temp, "${weatherEmoji(edmWeather.code, edmNow.hour)} ${edmWeather.temp}°")
            views.setTextViewText(R.id.tv_edm_weather_desc, "${weatherLabel(edmWeather.code)} • Gió ${edmWeather.wind}km/h")
            views.setInt(R.id.panel_edm, "setBackgroundResource", pickIosBackground(edmWeather.code, edmNow.hour))
            views.setInt(R.id.panel_edm, "setAlpha", panelAlpha)

            val intent = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            return views
        }

        private fun formatSolarDate(zdt: ZonedDateTime): String {
            val dow = when (zdt.dayOfWeek.value) {
                1 -> "Thứ Hai"
                2 -> "Thứ Ba"
                3 -> "Thứ Tư"
                4 -> "Thứ Năm"
                5 -> "Thứ Sáu"
                6 -> "Thứ Bảy"
                else -> "Chủ Nhật"
            }
            return "%s, %02d/%02d/%d".format(dow, zdt.dayOfMonth, zdt.monthValue, zdt.year)
        }

        fun pickIosBackground(code: Int, hour: Int): Int {
            return when {
                code in listOf(71, 73, 75, 77, 85, 86) -> R.drawable.bg_ios_snow
                code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> R.drawable.bg_ios_rain
                hour in 17..18 -> R.drawable.bg_ios_sunset
                hour >= 19 || hour < 6 -> R.drawable.bg_ios_night
                else -> R.drawable.bg_ios_day
            }
        }

        private fun weatherEmoji(code: Int, hour: Int): String {
            val isNight = hour >= 19 || hour < 6
            return when (code) {
                0 -> if (isNight) "🌙" else "☀️"
                1, 2 -> if (isNight) "☁️" else "⛅"
                3, 45, 48 -> "☁️"
                in 51..67, in 80..82 -> "🌧️"
                in 71..77, 85, 86 -> "❄️"
                in 95..99 -> "⛈️"
                else -> "🌤️"
            }
        }

        private fun weatherLabel(code: Int): String = when (code) {
            0 -> "Trời quang"
            1, 2 -> "Ít mây"
            3 -> "Nhiều mây"
            45, 48 -> "Sương mù"
            in 51..55 -> "Mưa phùn"
            in 61..67, in 80..82 -> "Có mưa"
            in 71..77, 85, 86 -> "Tuyết rơi"
            in 95..99 -> "Dông bão"
            else -> "Ổn định"
        }

        fun fetchWeather(lat: Double, lon: Double, fallback: WeatherInfo): WeatherInfo {
            return try {
                val urlStr = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m&timezone=auto"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                }
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                val json = JSONObject(text)
                val current = json.getJSONObject("current")
                WeatherInfo(
                    temp = Math.round(current.getDouble("temperature_2m")).toInt(),
                    code = current.getInt("weather_code"),
                    humidity = current.getInt("relative_humidity_2m"),
                    wind = Math.round(current.getDouble("wind_speed_10m")).toInt(),
                    isLive = true
                )
            } catch (_: Exception) {
                fallback.copy(isLive = false)
            }
        }
    }
}
