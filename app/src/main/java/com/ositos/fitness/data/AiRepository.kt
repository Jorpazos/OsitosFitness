package com.ositos.fitness.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.functions.functions
import com.ositos.fitness.BuildConfig
import com.ositos.fitness.domain.Dates
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

class AiLimitException : Exception("Llegaste al límite de 20 fotos por hoy. Mañana más 📸")

/**
 * Estimación de calorías por foto. La API key de Anthropic NUNCA está en el APK:
 * la app manda la foto achicada al backend (Cloud Function o Cloudflare Worker),
 * que es el único que habla con la API.
 */
class AiRepository(private val context: Context) {

    companion object {
        const val MAX_SIDE = 768
        const val JPEG_QUALITY = 70
        const val DAILY_LIMIT = 20
    }

    private val prefs = context.getSharedPreferences("ai_usage", Context.MODE_PRIVATE)

    fun usedToday(): Int = prefs.getInt("count_${Dates.todayKey()}", 0)

    private fun incrementLocal() {
        val k = "count_${Dates.todayKey()}"
        prefs.edit().putInt(k, prefs.getInt(k, 0) + 1).apply()
    }

    suspend fun analyze(uri: Uri): AiFoodResult = withContext(Dispatchers.IO) {
        // Freno local (además del freno del servidor) por si algo queda en loop.
        if (usedToday() >= DAILY_LIMIT) throw AiLimitException()
        val b64 = prepareImage(uri)
        incrementLocal()
        val json = if (BuildConfig.AI_BACKEND == "worker") callWorker(b64) else callFunction(b64)
        parse(json)
    }

    /** Reduce a máx. 768 px de lado y JPEG calidad 70, respetando la rotación EXIF. */
    private fun prepareImage(uri: Uri): String {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= MAX_SIDE) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, opts) }
            ?: error("No pude leer la foto")

        val rotation = runCatching {
            resolver.openInputStream(uri).use { input ->
                when (ExifInterface(input!!).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrDefault(0f)

        val scale = MAX_SIDE.toFloat() / max(decoded.width, decoded.height)
        val matrix = Matrix().apply {
            if (scale < 1f) postScale(scale, scale)
            if (rotation != 0f) postRotate(rotation)
        }
        val finalBmp = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        val out = ByteArrayOutputStream()
        finalBmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        if (finalBmp !== decoded) decoded.recycle()
        finalBmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    private suspend fun callFunction(b64: String): JSONObject {
        try {
            val result = Firebase.functions(BuildConfig.FUNCTIONS_REGION)
                .getHttpsCallable("analyzeFood")
                .call(mapOf("image" to b64, "mediaType" to "image/jpeg"))
                .await()
            @Suppress("UNCHECKED_CAST")
            return JSONObject(result.data as Map<String, Any?>)
        } catch (e: FirebaseFunctionsException) {
            if (e.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED) throw AiLimitException()
            throw Exception(e.message ?: "El backend de IA no respondió", e)
        }
    }

    private suspend fun callWorker(b64: String): JSONObject {
        val token = Firebase.auth.currentUser?.getIdToken(false)?.await()?.token
            ?: error("No hay sesión")
        val conn = (URL(BuildConfig.WORKER_URL.trimEnd('/') + "/analyze").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 60_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        conn.outputStream.use {
            it.write(JSONObject(mapOf("image" to b64, "mediaType" to "image/jpeg")).toString().toByteArray())
        }
        val code = conn.responseCode
        val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() } ?: ""
        if (code == 429) throw AiLimitException()
        if (code !in 200..299) error("El backend respondió $code")
        return JSONObject(body)
    }

    private fun parse(o: JSONObject): AiFoodResult {
        val arr = o.optJSONArray("alimentos")
        val foods = buildList {
            if (arr != null) for (i in 0 until arr.length()) {
                val f = arr.optJSONObject(i) ?: continue
                add(
                    AiFood(
                        name = f.optString("nombre", "Algo rico"),
                        portion = f.optString("porcion_estimada", ""),
                        kcal = f.optDouble("kcal", 0.0).roundToInt().coerceAtLeast(0),
                    ),
                )
            }
        }
        val total = o.optDouble("kcal_total", foods.sumOf { it.kcal }.toDouble()).roundToInt()
        return AiFoodResult(foods, total, o.optString("confianza", "media"))
    }
}
