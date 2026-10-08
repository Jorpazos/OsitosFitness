package com.ositos.fitness.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.QuotaExceededException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.ServiceDisabledException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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
 * Estimación de calorías por foto. Tres backends posibles (ositos.aiBackend en gradle.properties):
 *  - "gemini" (por defecto, gratis, sin tarjeta): Gemini vía Firebase AI Logic. No hay ninguna
 *    API key en el APK: Firebase hace de intermediario.
 *  - "firebase": Claude vía Cloud Function (requiere plan Blaze y API key de Anthropic en el backend).
 *  - "worker": Claude vía Cloudflare Worker.
 */
class AiRepository(private val context: Context) {

    companion object {
        const val MAX_SIDE = 768
        const val JPEG_QUALITY = 70
        const val DAILY_LIMIT = 20

        /** Se prueban en orden: si un nombre de modelo deja de existir, pasa al siguiente. */
        private val GEMINI_MODELS = listOf("gemini-flash-latest", "gemini-2.5-flash", "gemini-2.0-flash")

        private const val SYSTEM_PROMPT =
            "Sos un nutricionista que estima calorías a partir de fotos de comida (cocina argentina incluida). " +
                "Identificá cada alimento visible, estimá la porción y sus kcal aproximadas. " +
                "Es una estimación orientativa: si dudás, elegí valores típicos y bajá la confianza. " +
                "Respondé SOLO con JSON con el formato pedido. Si no hay comida en la foto, devolvé " +
                "alimentos vacío, kcal_total 0 y confianza \"baja\". Nombres en español."

        private val FOOD_SCHEMA = Schema.obj(
            mapOf(
                "alimentos" to Schema.array(
                    Schema.obj(
                        mapOf(
                            "nombre" to Schema.string(),
                            "porcion_estimada" to Schema.string(),
                            "kcal" to Schema.integer(),
                        ),
                    ),
                ),
                "kcal_total" to Schema.integer(),
                "confianza" to Schema.enumeration(listOf("baja", "media", "alta")),
            ),
        )
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
        val jpeg = prepareImage(uri)
        val json = when (BuildConfig.AI_BACKEND) {
            "worker" -> callWorker(jpeg.toBase64())
            "firebase" -> callFunction(jpeg.toBase64())
            else -> callGemini(jpeg)
        }
        // Solo cuenta para el límite diario si la IA respondió.
        incrementLocal()
        parse(json)
    }

    private fun ByteArray.toBase64(): String = Base64.encodeToString(this, Base64.NO_WRAP)

    /** Reduce a máx. 768 px de lado y JPEG calidad 70, respetando la rotación EXIF. */
    private fun prepareImage(uri: Uri): ByteArray {
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
        return out.toByteArray()
    }

    private suspend fun callGemini(jpeg: ByteArray): JSONObject {
        var last: Exception? = null
        for (name in GEMINI_MODELS) {
            try {
                val model = Firebase.ai(backend = GenerativeBackend.googleAI()).generativeModel(
                    modelName = name,
                    generationConfig = generationConfig {
                        responseMimeType = "application/json"
                        responseSchema = FOOD_SCHEMA
                    },
                    systemInstruction = content { text(SYSTEM_PROMPT) },
                )
                val response = model.generateContent(
                    content {
                        inlineData(jpeg, "image/jpeg")
                        text("Estimá las calorías de esta comida. Solo JSON.")
                    },
                )
                val text = response.text ?: error("La IA no devolvió respuesta")
                val start = text.indexOf('{')
                val end = text.lastIndexOf('}')
                if (start == -1 || end <= start) error("La IA no devolvió JSON")
                return JSONObject(text.substring(start, end + 1))
            } catch (e: ServiceDisabledException) {
                throw Exception(
                    "Falta activar la IA en Firebase: consola → Servicios de IA → AI Logic → Comenzar → Gemini Developer API.",
                    e,
                )
            } catch (e: QuotaExceededException) {
                throw Exception("Se agotó la cuota gratis de la IA por ahora. Probá en un rato o cargala a mano.", e)
            } catch (e: Exception) {
                last = e
                val msg = e.message.orEmpty().lowercase()
                // Modelo inexistente o retirado: probamos el siguiente de la lista.
                if ("not found" in msg || "404" in msg || "is not supported" in msg) continue
                throw Exception("La IA no pudo analizar la foto: ${e.message}", e)
            }
        }
        throw Exception("Ningún modelo de Gemini disponible: ${last?.message}", last)
    }

    private suspend fun callFunction(b64: String): JSONObject {
        try {
            val result = Firebase.functions(BuildConfig.FUNCTIONS_REGION)
                .getHttpsCallable("analyzeFood")
                .call(mapOf("image" to b64, "mediaType" to "image/jpeg"))
                .await()
            @Suppress("UNCHECKED_CAST")
            return JSONObject(result.getData() as Map<String, Any?>)
        } catch (e: FirebaseFunctionsException) {
            val msg = when (e.code) {
                FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED -> throw AiLimitException()
                FirebaseFunctionsException.Code.NOT_FOUND ->
                    "La IA de fotos todavía no está activada (falta subir el backend). Mientras tanto, cargala a mano desde 🍽️ Comida."
                FirebaseFunctionsException.Code.UNAVAILABLE, FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    "Sin conexión con la IA. Revisá internet y probá de nuevo."
                FirebaseFunctionsException.Code.INTERNAL ->
                    e.message?.takeIf { it != "INTERNAL" } ?: "La IA tuvo un problema. Probá de nuevo en un ratito."
                else -> e.message ?: "El backend de IA no respondió"
            }
            throw Exception(msg, e)
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
