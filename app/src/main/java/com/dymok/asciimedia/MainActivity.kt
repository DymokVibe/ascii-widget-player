package com.dymok.asciimedia

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.switchmaterial.SwitchMaterial

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var switchAmoled: SwitchMaterial
    private lateinit var switchMonetCover: SwitchMaterial
    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        tvStatus = findViewById(R.id.tv_permission_status)
        switchAmoled = findViewById(R.id.switch_amoled_bg)
        switchMonetCover = findViewById(R.id.switch_monet_cover)

        switchAmoled.isChecked = Prefs.isAmoledBg(this)
        switchMonetCover.isChecked = Prefs.isMonetCover(this)

        findViewById<Button>(R.id.btn_grant_permission).setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        findViewById<Button>(R.id.btn_apply_widget).setOnClickListener {
            Prefs.setAmoledBg(this, switchAmoled.isChecked)
            Prefs.setMonetCover(this, switchMonetCover.isChecked)
            MediaWidgetProvider.requestUpdate(this)

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                val resultValue = Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                setResult(Activity.RESULT_OK, resultValue)
            }
            finish()
        }
    }

    override fun onResume() {
        super.onResume()
        val granted = MediaRepository.isNotificationAccessGranted(this)
        tvStatus.text = if (granted) {
            "[ STATUS: ACTIVE // LISTENER READY ]"
        } else {
            "[ STATUS: MISSING NOTIFICATION ACCESS ]"
        }
        MediaWidgetProvider.requestUpdate(this)
    }
}
