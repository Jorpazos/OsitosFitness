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
    const val CHANNEL_WATER = "agua"
    const val CHANNEL_STORIES = "historias"

    const val ID_WATER = 9100
    const val ID_STORY = 9200
    const val ID_DONE = 9300

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_POKES, "Pinchazos", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Cuando tu compa te pincha 📌"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_REMINDERS, "Recordatorios y rachas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Comidas del día y empujoncitos para no perder la racha"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_WATER, "Agua", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Recordatorios para tomar agua durante el día 💧"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_STORIES, "Historias", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Cuando tu compa sube una historia y si la racha de historias está en peligro"
            },
        )
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun openApp(context: Context, id: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    fun show(
        context: Context,
        id: Int,
        title: String,
        body: String,
        channel: String = CHANNEL_POKES,
        actions: List<NotificationCompat.Action> = emptyList(),
    ) {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, id))
            .setPriority(if (channel == CHANNEL_POKES) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .apply { actions.forEach { addAction(it) } }
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    /** Confirmación cortita y silenciosa (ej.: "💧 ¡Anotado!") que se va sola a los pocos segundos. */
    fun showQuiet(context: Context, id: Int, title: String, body: String = "") {
        if (!canNotify(context)) return
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setSilent(true)
            .setAutoCancel(true)
            .setTimeoutAfter(5_000)
            .setContentIntent(openApp(context, id))
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }

    private fun actionIntent(context: Context, action: String, notificationId: Int): PendingIntent {
        val intent = Intent(context, ActionReceiver::class.java)
            .setAction(action)
            .putExtra(ActionReceiver.EXTRA_NOTIFICATION_ID, notificationId)
        return PendingIntent.getBroadcast(
            context, notificationId, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /** Botón "+1 vaso" en el recordatorio de agua: suma el vaso sin abrir la app. */
    fun waterAction(context: Context, notificationId: Int = ID_WATER) =
        NotificationCompat.Action(0, "💧 +1 vaso", actionIntent(context, ActionReceiver.ACTION_WATER, notificationId))

    /** Botón "Pincharle de vuelta" en los pinchazos. */
    fun pokeBackAction(context: Context, notificationId: Int) =
        NotificationCompat.Action(0, "📌 Pincharle de vuelta", actionIntent(context, ActionReceiver.ACTION_POKE_BACK, notificationId))
}
