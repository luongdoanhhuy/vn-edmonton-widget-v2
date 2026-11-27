package com.huyluong.vnedmontonwidget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {

    private val uiScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContentView(R.layout.activity_main)
            WeatherSyncWorker.schedule(this)

            val seekAlpha = findViewById<SeekBar>(R.id.seek_alpha)
            val tvAlpha = findViewById<TextView>(R.id.tv_alpha_value)
            val savedPct = DualTimeWeatherWidget.getSavedTransparency(this)

            seekAlpha?.progress = savedPct
            tvAlpha?.text = "$savedPct% Trong suốt"
            applyPreviewTransparency(savedPct)

            seekAlpha?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    tvAlpha?.text = "$progress% Trong suốt"
                    applyPreviewTransparency(progress)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    val pct = seekBar?.progress ?: 60
                    DualTimeWeatherWidget.saveTransparency(this@MainActivity, pct)
                    DualTimeWeatherWidget.syncAndUpdateAll(this@MainActivity)
                }
            })

            refreshPreview(forceNetwork = true)

            findViewById<Button>(R.id.btn_refresh)?.setOnClickListener {
                refreshPreview(forceNetwork = true)
                DualTimeWeatherWidget.syncAndUpdateAll(this)
                Toast.makeText(this, "Đang đồng bộ nhiệt độ & độ trong suốt...", Toast.LENGTH_SHORT).show()
            }

            findViewById<Button>(R.id.btn_pin_widget)?.setOnClickListener {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val appWidgetManager = getSystemService(AppWidgetManager::class.java)
                        val myProvider = ComponentName(this, DualTimeWeatherWidget::class.java)
                        if (appWidgetManager != null && appWidgetManager.isRequestPinAppWidgetSupported) {
                            appWidgetManager.requestPinAppWidget(myProvider, null, null)
                        } else {
                            Toast.makeText(this, "Nhấn giữ màn hình chính -> Tiện ích -> VN - Edmonton.", Toast.LENGTH_LONG).show()
                        }
                    }
                } catch (_: Exception) {
                    Toast.makeText(this, "Nhấn giữ màn hình chính -> Tiện ích -> VN - Edmonton.", Toast.LENGTH_LONG).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Lỗi khởi tạo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPreview(forceNetwork = true)
        DualTimeWeatherWidget.syncAndUpdateAll(this)
    }

    private fun applyPreviewTransparency(pct: Int) {
        try {
            val alphaFloat = (1.0f - (pct / 100f) * 0.85f).coerceIn(0.15f, 1.0f)
            findViewById<LinearLayout>(R.id.panel_vn)?.alpha = alphaFloat
            findViewById<LinearLayout>(R.id.panel_edm)?.alpha = alphaFloat
            findViewById<LinearLayout>(R.id.widget_root)?.background?.alpha = (alphaFloat * 255).toInt()
        } catch (_: Exception) {
        }
    }

    private fun refreshPreview(forceNetwork: Boolean) {
        val (cachedVn, cachedEdm) = DualTimeWeatherWidget.getCachedWeather(this)
        renderData(cachedVn, cachedEdm)

        if (!forceNetwork) return

        uiScope.launch {
            try {
                val (vnW, edmW) = withContext(Dispatchers.IO) {
                    DualTimeWeatherWidget.fetchBothLive(this@MainActivity)
                }
                renderData(vnW, edmW)
            } catch (_: Exception) {
            }
        }
    }

    private fun renderData(
        vnW: DualTimeWeatherWidget.Companion.WeatherInfo,
        edmW: DualTimeWeatherWidget.Companion.WeatherInfo
    ) {
        try {
            val vnNow = ZonedDateTime.now(ZoneId.of("Asia/Ho_Chi_Minh"))
            val edmNow = ZonedDateTime.now(ZoneId.of("America/Edmonton"))
            val diffHours = ChronoUnit.HOURS.between(vnNow.toLocalDateTime(), edmNow.toLocalDateTime())

            val vnLunar = LunarCalendar.convertSolar2Lunar(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year, 7.0)
            findViewById<TextView>(R.id.tv_vn_offset)?.text = "Hôm nay • +${-diffHours}g"
            findViewById<TextView>(R.id.tv_vn_solar)?.text = "%02d/%02d/%d".format(vnNow.dayOfMonth, vnNow.monthValue, vnNow.year)
            findViewById<TextView>(R.id.tv_vn_lunar)?.text = vnLunar.formatShort()
            findViewById<TextView>(R.id.tv_vn_temp)?.text = "${vnW.temp}°"
            findViewById<TextView>(R.id.tv_vn_weather_desc)?.text = "Ẩm ${vnW.humidity}% • Gió ${vnW.wind}km/h"
            findViewById<LinearLayout>(R.id.panel_vn)?.setBackgroundResource(
                DualTimeWeatherWidget.pickIosBackground(vnW.code, vnNow.hour)
            )

            val edmDayLabel = when {
                edmNow.toLocalDate().isBefore(vnNow.toLocalDate()) -> "Hôm qua"
                edmNow.toLocalDate().isAfter(vnNow.toLocalDate()) -> "Ngày mai"
                else -> "Hôm nay"
            }
            val edmLunar = LunarCalendar.convertSolar2Lunar(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year, 7.0)
            findViewById<TextView>(R.id.tv_edm_offset)?.text = "$edmDayLabel • ${diffHours}g"
            findViewById<TextView>(R.id.tv_edm_solar)?.text = "%02d/%02d/%d".format(edmNow.dayOfMonth, edmNow.monthValue, edmNow.year)
            findViewById<TextView>(R.id.tv_edm_lunar)?.text = edmLunar.formatShort()
            findViewById<TextView>(R.id.tv_edm_temp)?.text = "${edmW.temp}°"
            findViewById<TextView>(R.id.tv_edm_weather_desc)?.text = "Ẩm ${edmW.humidity}% • Gió ${edmW.wind}km/h"
            findViewById<LinearLayout>(R.id.panel_edm)?.setBackgroundResource(
                DualTimeWeatherWidget.pickIosBackground(edmW.code, edmNow.hour)
            )

            val statusSuffix = if (vnW.isLive && edmW.isLive) "Trực tiếp" else "Lưu tạm"
            findViewById<TextView>(R.id.tv_sync_status)?.text =
                "Cập nhật: ${DualTimeWeatherWidget.getLastSyncText(this)} ($statusSuffix) • Tự động mỗi 15 phút"

            applyPreviewTransparency(DualTimeWeatherWidget.getSavedTransparency(this))
        } catch (_: Exception) {
        }
    }
}
