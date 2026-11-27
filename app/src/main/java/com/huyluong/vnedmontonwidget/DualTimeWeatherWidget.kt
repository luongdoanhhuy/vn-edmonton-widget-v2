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
import java.net.URL
import java.time.ZoneId
import java.time.ZonedDateTime

class DualTimeWeatherWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (id in appWidgetIds) {
            updateWidget(context, appWidgetManager, id)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    companion object {
        data class WeatherInfo(val temp: Int, val code: Int, val humidity: Int, val wind: Int)

        fun updateAllWidgets(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, DualTimeWeatherWidget::class.java))
            for (id in ids) {
                updateWidget(context, manager, id)
            }
        }

        fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val options = manager.getAppWidgetOptions(widgetId)
            val minWidth = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 280)
            val minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 130)

            CoroutineScope(Dispatchers.IO).launch {
                val vnWeather = fetchWeather(10.8231, 106.6297)
                val edmWeather = fetchWeather(53.5461, -113.4938)

                val views = buildRemoteViews(context, minWidth, minHeight, vnWeather, edmWeather)
                manager.updateAppWidget(widgetId, views)
            }
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
                isCompact -> 20f
                isTall -> 34f
                else -> 27f
            }
            val metaSize = if (isCompact) 9.5f else if (isTall) 12.5f else 11f

            views.setTextViewTextSize(R.id.clock_vn, TypedValue.COMPLEX_UNIT_SP, clockSize)
            views.setTextViewTextSize(R.id.clock_edm, TypedValue.COMPLEX_UNIT_SP, clockSize)
            views.setTextViewTextSize(R.id.tv_vn_solar, TypedValue.COMPLEX_UNIT_SP, metaSize)
            views.setTextViewTextSize(R.id.tv_edm_solar, TypedValue.COMPLEX_UNIT_SP, metaSize)
            views.setTextViewTextSize(R.id.tv_vn_lunar, TypedValue.COMPLEX_UNIT_SP, metaSize)
            views.setTextViewTextSize(R.id.tv_edm_lunar, TypedValue.COMPLEX_UNIT_SP, metaSize)

            val descVisibility = if (heightDp < 105) View.GONE else View.VISIBLE
            views.setViewVisibility(R.id.tv_vn_weather_desc, descVisibility)
            views.setViewVisibility(R.id.tv_edm_weather_desc, descVisibility)

            val vnNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            val vnLunar = LunarCalendar.convertSolar2Lunar(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year, 7.0)
            views.setTextViewText(R.id.tv_vn_solar, formatSolarDate(vnNow))
            views.setTextViewText(R.id.tv_vn_lunar, vnLunar.formatShort())
            views.setTextViewText(R.id.tv_vn_temp, "${weatherEmoji(vnWeather.code, vnNow.hour)} ${vnWeather.temp}°C")
            views.setTextViewText(R.id.tv_vn_weather_desc, "${weatherLabel(vnWeather.code)} • Ẩm ${vnWeather.humidity}%")
            views.setInt(R.id.panel_vn, "setBackgroundResource", pickBackground(vnWeather.code, vnNow.hour))

            val edmNow = ZonedDateTime.now(ZoneId.of("America/Edmonton"))
            val edmLunar = LunarCalendar.convertSolar2Lunar(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year, 7.0)
            views.setTextViewText(R.id.tv_edm_solar, formatSolarDate(edmNow))
            views.setTextViewText(R.id.tv_edm_lunar, edmLunar.formatShort())
            views.setTextViewText(R.id.tv_edm_temp, "${weatherEmoji(edmWeather.code, edmNow.hour)} ${edmWeather.temp}°C")
            views.setTextViewText(R.id.tv_edm_weather_desc, "${weatherLabel(edmWeather.code)} • Gió ${edmWeather.wind}km/h")
            views.setInt(R.id.panel_edm, "setBackgroundResource", pickBackground(edmWeather.code, edmNow.hour))

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

        private fun pickBackground(code: Int, hour: Int): Int {
            return when {
                code in listOf(71, 73, 75, 77, 85, 86) -> R.drawable.bg_snow
                code in listOf(51, 53, 55, 61, 63, 65, 80, 81, 82, 95, 96, 99) -> R.drawable.bg_rain
                hour in 17..18 -> R.drawable.bg_sunset
                hour >= 19 || hour < 6 -> R.drawable.bg_night_clear
                else -> R.drawable.bg_day_clear
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

        fun fetchWeather(lat: Double, lon: Double): WeatherInfo {
            return try {
                val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&current=temperature_2m,relative_humidity_2m,weather_code,wind_speed_10m"
                val json = JSONObject(URL(url).readText())
                val current = json.getJSONObject("current")
                WeatherInfo(
                    temp = Math.round(current.getDouble("temperature_2m")).toInt(),
                    code = current.getInt("weather_code"),
                    humidity = current.getInt("relative_humidity_2m"),
                    wind = Math.round(current.getDouble("wind_speed_10m")).toInt()
                )
            } catch (e: Exception) {
                WeatherInfo(25, 0, 65, 10)
            }
        }
    }
}
