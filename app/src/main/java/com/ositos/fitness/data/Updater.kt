package com.ositos.fitness.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.ositos.fitness.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class UpdateInfo(val versionCode: Int, val versionName: String, val apkUrl: String, val notes: String)

/**
 * Actualizaciones desde GitHub Releases: cada push a main publica un release "vN" con el APK.
 * La app compara N con su versionCode, baja el APK y abre el instalador de Android.
 * (Requiere que el repo sea público: los releases de repos privados piden login.)
 */
class Updater(private val context: Context) {

    private val prefs = context.getSharedPreferences("updater", Context.MODE_PRIVATE)

    val currentVersion: Int get() = BuildConfig.VERSION_CODE
    val currentVersionName: String get() = BuildConfig.VERSION_NAME

    /** Chequeo automático como mucho cada 6 horas. */
    fun shouldAutoCheck(): Boolean =
        System.currentTimeMillis() - prefs.getLong("lastCheck", 0) > 6 * 3_600_000L

    /** Devuelve la versión nueva si hay una; null si estás al día. Tira excepción si no se pudo consultar. */
    suspend fun check(): UpdateInfo? = withContext(Dispatchers.IO) {
        prefs.edit().putLong("lastCheck", System.currentTimeMillis()).apply()
        val conn = (URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "OsitosFitness-Android")
        }
        when (val code = conn.responseCode) {
            200 -> Unit
            404 -> error("Todavía no hay versiones publicadas (o el repo es privado)")
            else -> error("GitHub respondió $code")
        }
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val tag = json.optString("tag_name")
        val version = tag.removePrefix("v").toIntOrNull() ?: return@withContext null
        val assets = json.optJSONArray("assets")
        var apk: String? = null
        if (assets != null) for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk")) apk = a.optString("browser_download_url")
        }
        if (apk == null || version <= currentVersion) return@withContext null
        UpdateInfo(
            versionCode = version,
            versionName = json.optString("name").ifBlank { "1.$version" },
            apkUrl = apk,
            notes = json.optString("body").lines().firstOrNull { it.isNotBlank() }.orEmpty().take(200),
        )
    }

    /** Baja el APK a la caché, informando el progreso (0..1). */
    suspend fun download(info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "ositos-fitness-${info.versionCode}.apk")
        var url = URL(info.apkUrl)
        var conn: HttpURLConnection
        var redirects = 0
        while (true) {
            conn = (url.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "OsitosFitness-Android")
            }
            val code = conn.responseCode
            if (code in 300..399 && redirects < 5) {
                url = URL(url, conn.getHeaderField("Location"))
                redirects++
                continue
            }
            if (code != 200) error("No se pudo descargar ($code)")
            break
        }
        val total = conn.contentLengthLong
        conn.inputStream.use { input ->
            file.outputStream().use { out ->
                val buf = ByteArray(64 * 1024)
                var read: Int
                var done = 0L
                while (input.read(buf).also { read = it } >= 0) {
                    out.write(buf, 0, read)
                    done += read
                    if (total > 0) onProgress(done.toFloat() / total)
                }
            }
        }
        file
    }

    /** ¿Android ya nos dejó instalar apps? (permiso "Instalar apps desconocidas" para Ositos). */
    fun canInstall(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings() {
        val i = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }

    /** Abre el instalador de Android con el APK descargado (se instala encima, sin perder nada). */
    fun install(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(i)
    }
}
