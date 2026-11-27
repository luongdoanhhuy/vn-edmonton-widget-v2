package com.huyluong.vnedmontonwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        refreshPreview()

        findViewById<Button>(R.id.btn_refresh).setOnClickListener {
            refreshPreview()
            DualTimeWeatherWidget.updateAllWidgets(this)
            Toast.makeText(this, "Đã cập nhật thời tiết & lịch!", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btn_pin_widget).setOnClickListener {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val appWidgetManager = getSystemService(AppWidgetManager::class.java)
                val myProvider = ComponentName(this, DualTimeWeatherWidget::class.java)
                if (appWidgetManager.isRequestPinAppWidgetSupported) {
                    appWidgetManager.requestPinAppWidget(myProvider, null, null)
                } else {
                    Toast.makeText(this, "Vui lòng thêm Widget từ màn hình chính điện thoại.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun refreshPreview() {
        CoroutineScope(Dispatchers.IO).launch {
            val vnW = DualTimeWeatherWidget.fetchWeather(10.8231, 106.6297)
            val edmW = DualTimeWeatherWidget.fetchWeather(53.5461, -113.4938)

            withContext(Dispatchers.Main) {
                val vnNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
                val vnLunar = LunarCalendar.convertSolar2Lunar(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year, 7.0)
                findViewById<TextView>(R.id.tv_vn_solar).text = "%02d/%02d/%d".format(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year)
                findViewById<TextView>(R.id.tv_vn_lunar).text = vnLunar.formatShort()
                findViewById<TextView>(R.id.tv_vn_temp).text = "${vnW.temp}°C"
                findViewById<TextView>(R.id.tv_vn_weather_desc).text = "Độ ẩm ${vnW.humidity}% • Gió ${vnW.wind}km/h"
                findViewById<LinearLayout>(R.id.panel_vn).setBackgroundResource(
                    if (vnNow.hour in 6..18) R.drawable.bg_day_clear else R.drawable.bg_night_clear
                )

                val edmNow = ZonedDateTime.now(ZoneId.of("America/Edmonton"))
                val edmLunar = LunarCalendar.convertSolar2Lunar(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year, 7.0)
                findViewById<TextView>(R.id.tv_edm_solar).text = "%02d/%02d/%d".format(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year)
                findViewById<TextView>(R.id.tv_edm_lunar).text = edmLunar.formatShort()
                findViewById<TextView>(R.id.tv_edm_temp).text = "${edmW.temp}°C"
                findViewById<TextView>(R.id.tv_edm_weather_desc).text = "Độ ẩm ${edmW.humidity}% • Gió ${edmW.wind}km/h"
                findViewById<LinearLayout>(R.id.panel_edm).setBackgroundResource(
                    if (edmNow.hour in 6..18) R.drawable.bg_day_clear else R.drawable.bg_night_clear
                )
            }
        }
    }
}
