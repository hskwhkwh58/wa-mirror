package com.wamirror

import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var statusDot: TextView
    private lateinit var statusLabel: TextView
    private lateinit var btnSettings: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusDot = findViewById(R.id.statusDot)
        statusLabel = findViewById(R.id.statusLabel)
        btnSettings = findViewById(R.id.btnSettings)

        btnSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    private fun updateStatus() {
        val enabled = isNotificationServiceEnabled()
        if (enabled) {
            statusDot.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            statusDot.text = "●"
            statusLabel.text = "Notification listener: ACTIVE"
        } else {
            statusDot.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            statusDot.text = "●"
            statusLabel.text = "Notification listener: DISABLED\nTap below to enable"
        }
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners") ?: return false
        if (flat.isEmpty()) return false
        val names = flat.split(":")
        val component = ComponentName(this, WANotificationService::class.java)
        for (name in names) {
            if (ComponentName.unflattenFromString(name.trim()) == component) return true
        }
        return false
    }
}
