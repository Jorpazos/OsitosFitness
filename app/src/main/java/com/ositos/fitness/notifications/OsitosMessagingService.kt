package com.ositos.fitness.notifications

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.ositos.fitness.OsitosApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Recibe los pinchazos que manda la Cloud Function (mensajes "data", así siempre los dibujamos nosotros). */
class OsitosMessagingService : FirebaseMessagingService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        val repo = (application as OsitosApp).container.repo
        scope.launch { runCatching { repo.saveFcmToken(token) } }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: "📌 ¡Te pincharon!"
        val body = data["body"] ?: message.notification?.body ?: ""
        val channel = if (data["type"] == "reminder") Notifications.CHANNEL_REMINDERS else Notifications.CHANNEL_POKES
        val id = (data["pokeId"] ?: message.messageId ?: System.currentTimeMillis().toString()).hashCode()
        Notifications.show(this, id, title, body, channel)
    }
}
