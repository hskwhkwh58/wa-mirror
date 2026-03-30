package com.wamirror

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class WANotificationService : NotificationListenerService() {

    companion object {
        private const val SERVER_URL = "http://54.176.73.11:7070/notify"
        private const val SECRET = "Vmos@123"
        private const val CHANNEL_ID = "wa_mirror_fg"

        private val WHATSAPP_PACKAGES = setOf(
            "com.whatsapp",
            "com.whatsapp.w4b"
        )
    }

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        createForegroundChannel()
        val notification = android.app.Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("WA Mirror")
            .setContentText("Listening for WhatsApp notifications")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notification)
    }

    override fun onStartCommand(intent: android.content.Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn ?: return
        if (sbn.packageName !in WHATSAPP_PACKAGES) return

        val extras: Bundle = sbn.notification?.extras ?: return
        val sender = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: return
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: return

        // Skip group summaries and empty messages
        if (sender.isBlank() || message.isBlank()) return
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        val appName = when (sbn.packageName) {
            "com.whatsapp.w4b" -> "WhatsApp Business"
            else -> "WhatsApp"
        }
        val timestamp = (sbn.postTime / 1000).toString()

        postNotification(appName, sender, message, timestamp, retried = false)
    }

    private fun postNotification(
        appName: String,
        sender: String,
        message: String,
        timestamp: String,
        retried: Boolean
    ) {
        val json = JSONObject().apply {
            put("secret", SECRET)
            put("app", appName)
            put("sender", sender)
            put("message", message)
            put("timestamp", timestamp)
        }

        val body = json.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(SERVER_URL).post(body).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!retried) {
                    postNotification(appName, sender, message, timestamp, retried = true)
                }
            }

            override fun onResponse(call: Call, response: Response) {
                response.close()
            }
        })
    }

    private fun createForegroundChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "WA Mirror Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps WA Mirror running in background"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }
}
