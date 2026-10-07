package com.ositos.fitness

import android.app.Application
import android.content.Context
import com.google.firebase.FirebaseApp
import com.ositos.fitness.data.AiRepository
import com.ositos.fitness.data.DuoRepository
import com.ositos.fitness.notifications.DuoWorker
import com.ositos.fitness.notifications.Notifications

/** Inyección de dependencias "a mano": para una app de 2 personas no hace falta Hilt. */
class AppContainer(context: Context) {
    val repo by lazy { DuoRepository() }
    val ai by lazy { AiRepository(context) }
    val prefs = context.getSharedPreferences("ositos", Context.MODE_PRIVATE)
}

class OsitosApp : Application() {
    lateinit var container: AppContainer
        private set

    /** true si se compiló con el google-services.json de ejemplo (sin Firebase real). */
    var firebasePlaceholder = false
        private set

    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        firebasePlaceholder = runCatching { FirebaseApp.getInstance().options.projectId }
            .getOrNull().let { it == null || it == "ositos-placeholder" }
        container = AppContainer(this)
        Notifications.createChannels(this)
        if (!firebasePlaceholder) DuoWorker.schedule(this)
    }
}
