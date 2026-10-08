/**
 * Backend de Ositos Fitness (Firebase Cloud Functions v2, requiere plan Blaze).
 *
 *  - analyzeFood: recibe una foto (JPEG base64, ya reducida por la app) y devuelve la estimación
 *    de calorías usando la API de Anthropic. La API key vive SOLO acá, como secreto de Firebase.
 *  - onPokeCreated: cuando alguien "pincha" a su pareja, manda la notificación push (FCM).
 */
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const { onDocumentCreated } = require("firebase-functions/v2/firestore");
const { defineSecret } = require("firebase-functions/params");
const logger = require("firebase-functions/logger");
const { initializeApp } = require("firebase-admin/app");
const { getFirestore } = require("firebase-admin/firestore");
const { getMessaging } = require("firebase-admin/messaging");
const Anthropic = require("@anthropic-ai/sdk").default;

initializeApp();
const db = getFirestore();

// Debe coincidir con BuildConfig.FUNCTIONS_REGION de la app y con la ubicación de Firestore.
const REGION = "southamerica-east1";
const TZ = "America/Argentina/Buenos_Aires";
const DUO = "duos/main";
const DAILY_PHOTO_LIMIT = 20;
const MAX_POKES_PER_DAY = 3;
const MAX_IMAGE_B64_CHARS = 1_500_000; // ~1,1 MB; una foto de 768px al 70% pesa ~60-150 KB

const ANTHROPIC_API_KEY = defineSecret("ANTHROPIC_API_KEY");

// Más preciso que Haiku para estimar porciones; ~US$0,004 por foto.
const MODEL = "claude-sonnet-5-5";

const SYSTEM_PROMPT =
  "Sos un nutricionista que estima calorías a partir de fotos de comida (cocina argentina incluida). " +
  "Identificá cada alimento visible, estimá la porción y sus kcal aproximadas. " +
  "Es una estimación orientativa: si dudás, elegí valores típicos y bajá la confianza. " +
  "Respondé SOLO con JSON válido, sin texto extra, con este formato exacto: " +
  '{"alimentos":[{"nombre":"","porcion_estimada":"","kcal":0}],"kcal_total":0,"confianza":"baja|media|alta"}. ' +
  'Si no hay comida en la foto, devolvé alimentos vacío, kcal_total 0 y confianza "baja". Nombres en español.';

const FOOD_SCHEMA = {
  type: "object",
  properties: {
    alimentos: {
      type: "array",
      items: {
        type: "object",
        properties: {
          nombre: { type: "string" },
          porcion_estimada: { type: "string" },
          kcal: { type: "integer" },
        },
        required: ["nombre", "porcion_estimada", "kcal"],
        additionalProperties: false,
      },
    },
    kcal_total: { type: "integer" },
    confianza: { type: "string", enum: ["baja", "media", "alta"] },
  },
  required: ["alimentos", "kcal_total", "confianza"],
  additionalProperties: false,
};

function todayKey(date = new Date()) {
  // yyyy-mm-dd en hora argentina
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(date);
}

async function assertMember(uid) {
  const duo = await db.doc(DUO).get();
  const members = (duo.exists && duo.get("members")) || [];
  if (!members.includes(uid)) {
    throw new HttpsError("permission-denied", "No sos parte de este dúo.");
  }
}

/** Freno de seguridad: máx. 20 fotos por persona por día (por si algo queda en loop). */
async function consumePhotoQuota(uid) {
  const ref = db.doc(`${DUO}/aiUsage/${uid}_${todayKey()}`);
  await db.runTransaction(async (tx) => {
    const snap = await tx.get(ref);
    const count = snap.exists ? snap.get("count") || 0 : 0;
    if (count >= DAILY_PHOTO_LIMIT) {
      throw new HttpsError("resource-exhausted", "Límite de 20 fotos por día alcanzado.");
    }
    tx.set(ref, { uid, day: todayKey(), count: count + 1, updatedAt: Date.now() }, { merge: true });
  });
}

/** Saca el primer objeto JSON del texto (por si el modelo agregara algo alrededor). */
function extractJson(text) {
  const start = text.indexOf("{");
  const end = text.lastIndexOf("}");
  if (start === -1 || end <= start) throw new Error("La respuesta no tiene JSON");
  return JSON.parse(text.slice(start, end + 1));
}

function normalize(raw) {
  const alimentos = Array.isArray(raw.alimentos)
    ? raw.alimentos.slice(0, 15).map((a) => ({
        nombre: String(a.nombre || "Alimento").slice(0, 60),
        porcion_estimada: String(a.porcion_estimada || "").slice(0, 60),
        kcal: Math.max(0, Math.round(Number(a.kcal) || 0)),
      }))
    : [];
  const suma = alimentos.reduce((acc, a) => acc + a.kcal, 0);
  const total = Math.max(0, Math.round(Number(raw.kcal_total) || suma));
  const confianza = ["baja", "media", "alta"].includes(raw.confianza) ? raw.confianza : "media";
  return { alimentos, kcal_total: total, confianza };
}

async function askClaude(apiKey, imageB64, mediaType) {
  const client = new Anthropic({ apiKey, maxRetries: 1, timeout: 45_000 });
  const request = {
    model: MODEL,
    max_tokens: 300,
    // Sin razonamiento extendido (en Sonnet 5.5 se apaga con "between_tools"):
    // los 300 tokens quedan para la respuesta y contesta más rápido.
    thinking: { type: "between_tools" },
    system: SYSTEM_PROMPT,
    messages: [
      {
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mediaType, data: imageB64 } },
          { type: "text", text: "Estimá las calorías de esta comida. Solo JSON." },
        ],
      },
    ],
  };

  let msg;
  try {
    // Salida estructurada: garantiza JSON con el esquema exacto.
    msg = await client.beta.messages.create({
      ...request,
      output_config: { effort: "medium", format: { type: "json_schema", schema: FOOD_SCHEMA } },
      // Si Sonnet 5.5 rechazara por un falso positivo de sus filtros, reintenta solo en otro modelo.
      betas: ["server-side-fallback-2026-07-01"],
      fallbacks: "default",
    });
  } catch (err) {
    if (err instanceof Anthropic.BadRequestError) {
      // Si el modelo no aceptara salida estructurada, reintentamos solo con el system prompt.
      logger.warn("Reintento sin output_config", { message: err.message });
      msg = await client.messages.create({ ...request, output_config: { effort: "medium" } });
    } else {
      throw err;
    }
  }

  if (msg.stop_reason === "refusal") {
    throw new HttpsError("failed-precondition", "No pude analizar esa foto. Probá con otra.");
  }
  const textBlock = msg.content.find((b) => b.type === "text");
  if (!textBlock) throw new Error(`Respuesta sin texto (stop_reason=${msg.stop_reason})`);
  return normalize(extractJson(textBlock.text));
}

exports.analyzeFood = onCall(
  {
    region: REGION,
    secrets: [ANTHROPIC_API_KEY],
    memory: "256MiB",
    timeoutSeconds: 60,
    maxInstances: 2,
  },
  async (request) => {
    const uid = request.auth && request.auth.uid;
    if (!uid) throw new HttpsError("unauthenticated", "Iniciá sesión primero.");
    await assertMember(uid);

    const image = request.data && request.data.image;
    const mediaType = (request.data && request.data.mediaType) || "image/jpeg";
    if (typeof image !== "string" || image.length < 100) {
      throw new HttpsError("invalid-argument", "Falta la imagen.");
    }
    if (image.length > MAX_IMAGE_B64_CHARS) {
      throw new HttpsError("invalid-argument", "La imagen es muy grande (máx. 768px, JPEG 70).");
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(mediaType)) {
      throw new HttpsError("invalid-argument", "Formato de imagen no soportado.");
    }

    await consumePhotoQuota(uid);

    try {
      const result = await askClaude(ANTHROPIC_API_KEY.value(), image, mediaType);
      logger.info("analyzeFood ok", { uid, total: result.kcal_total, n: result.alimentos.length });
      return result;
    } catch (err) {
      if (err instanceof HttpsError) throw err;
      logger.error("analyzeFood error", err);
      if (err instanceof Anthropic.RateLimitError) {
        throw new HttpsError("unavailable", "La IA está saturada, probá en un ratito.");
      }
      if (err instanceof Anthropic.AuthenticationError) {
        throw new HttpsError("internal", "La API key de Anthropic es inválida. Revisá el secreto.");
      }
      throw new HttpsError("internal", "No pude analizar la foto. Probá de nuevo.");
    }
  },
);

exports.onPokeCreated = onDocumentCreated(
  { document: `${DUO}/pokes/{pokeId}`, region: REGION },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const poke = snap.data();
    const pokeId = event.params.pokeId;

    // Máximo 3 pinchazos por día por persona (además del control en la app).
    const startOfDay = new Date(`${todayKey()}T00:00:00-03:00`).getTime();
    const sent = await db.collection(`${DUO}/pokes`).where("from", "==", poke.from).get();
    const sentToday = sent.docs.filter((d) => (d.get("ts") || 0) >= startOfDay).length;
    if (sentToday > MAX_POKES_PER_DAY) {
      logger.info("Pinchazo ignorado: límite diario", { from: poke.from });
      await snap.ref.update({ delivered: true, skipped: "limit" });
      return;
    }

    const profile = await db.doc(`${DUO}/profiles/${poke.to}`).get();
    if (!profile.exists) return;
    const token = profile.get("fcmToken");
    const mutedUntil = profile.get("pokesMutedUntil") || 0;
    if (mutedUntil > Date.now()) {
      await snap.ref.update({ delivered: true, skipped: "muted" });
      return;
    }
    if (!token) return; // queda pendiente: el respaldo de la app (WorkManager) lo muestra

    const fromName = poke.fromName || "Tu pareja";
    try {
      await getMessaging().send({
        token,
        data: {
          type: "poke",
          pokeId,
          title: `📌 ${fromName} te pinchó`,
          body: String(poke.message || "¡No aflojes!"),
        },
        android: { priority: "high", ttl: 6 * 3600 * 1000 },
      });
      await snap.ref.update({ delivered: true, pushedAt: Date.now() });
    } catch (err) {
      logger.warn("FCM falló", { code: err.code, message: err.message });
      if (err.code === "messaging/registration-token-not-registered") {
        await profile.ref.update({ fcmToken: null });
      }
    }
  },
);
