package com.ositos.fitness.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ositos.fitness.MainActivity
import com.ositos.fitness.R

object Notifications {
    const val CHANNEL_POKES = "pinchazos"
    const val CHANNEL_REMINDERS = "recordatorios"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POKES, "Pinchazos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando tu pareja te pincha 📌"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios y rachas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Empujoncitos para no perder la racha"
            },
        )
    }

    fun show(context: Context, id: Int, title: String, body: String, channel: String = CHANNEL_POKES) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(pi)
            .setPriority(if (channel == CHANNEL_POKES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }
}
