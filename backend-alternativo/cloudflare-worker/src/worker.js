/**
 * Ositos Fitness — backend de IA en Cloudflare Workers (plan gratuito).
 *
 * POST /analyze  { image: <jpeg base64>, mediaType: "image/jpeg" }
 * Header: Authorization: Bearer <Firebase ID token>
 *
 * Verifica el token de Firebase (firma de Google), aplica el límite de 20 fotos/día
 * (si hay KV configurado) y llama a la API de Anthropic. La API key es un secreto del Worker.
 */
import Anthropic from "@anthropic-ai/sdk";
import { createRemoteJWKSet, jwtVerify } from "jose";

const MODEL = "claude-haiku-5-5";
const DAILY_PHOTO_LIMIT = 20;
const MAX_IMAGE_B64_CHARS = 1_500_000;
const TZ = "America/Argentina/Buenos_Aires";

const GOOGLE_JWKS = createRemoteJWKSet(
  new URL("https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com"),
);

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

const json = (body, status = 200) =>
  new Response(JSON.stringify(body), { status, headers: { "content-type": "application/json" } });

function todayKey() {
  return new Intl.DateTimeFormat("en-CA", { timeZone: TZ }).format(new Date());
}

async function verifyUser(request, env) {
  const auth = request.headers.get("authorization") || "";
  const token = auth.startsWith("Bearer ") ? auth.slice(7) : null;
  if (!token) return null;
  try {
    const { payload } = await jwtVerify(token, GOOGLE_JWKS, {
      issuer: `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
      audience: env.FIREBASE_PROJECT_ID,
    });
    const allowed = (env.ALLOWED_EMAILS || "")
      .split(",")
      .map((s) => s.trim().toLowerCase())
      .filter(Boolean);
    if (allowed.length && !allowed.includes(String(payload.email || "").toLowerCase())) return null;
    return payload.sub;
  } catch {
    return null;
  }
}

async function consumeQuota(env, uid) {
  if (!env.USAGE) return true; // sin KV: queda solo el límite de la app
  const key = `${uid}_${todayKey()}`;
  const count = Number((await env.USAGE.get(key)) || 0);
  if (count >= DAILY_PHOTO_LIMIT) return false;
  await env.USAGE.put(key, String(count + 1), { expirationTtl: 60 * 60 * 48 });
  return true;
}

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

async function askClaude(env, image, mediaType) {
  const client = new Anthropic({ apiKey: env.ANTHROPIC_API_KEY, maxRetries: 1, timeout: 45_000 });
  const request = {
    model: MODEL,
    max_tokens: 300,
    thinking: { type: "disabled" },
    system: SYSTEM_PROMPT,
    messages: [
      {
        role: "user",
        content: [
          { type: "image", source: { type: "base64", media_type: mediaType, data: image } },
          { type: "text", text: "Estimá las calorías de esta comida. Solo JSON." },
        ],
      },
    ],
  };
  let msg;
  try {
    msg = await client.messages.create({
      ...request,
      output_config: { format: { type: "json_schema", schema: FOOD_SCHEMA } },
    });
  } catch (err) {
    if (err instanceof Anthropic.BadRequestError) msg = await client.messages.create(request);
    else throw err;
  }
  if (msg.stop_reason === "refusal") throw new Error("refusal");
  const textBlock = msg.content.find((b) => b.type === "text");
  if (!textBlock) throw new Error(`Respuesta sin texto (stop_reason=${msg.stop_reason})`);
  return normalize(extractJson(textBlock.text));
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    if (request.method !== "POST" || url.pathname !== "/analyze") return json({ error: "not_found" }, 404);

    const uid = await verifyUser(request, env);
    if (!uid) return json({ error: "unauthenticated" }, 401);

    let body;
    try {
      body = await request.json();
    } catch {
      return json({ error: "invalid_json" }, 400);
    }
    const image = body && body.image;
    const mediaType = (body && body.mediaType) || "image/jpeg";
    if (typeof image !== "string" || image.length < 100 || image.length > MAX_IMAGE_B64_CHARS) {
      return json({ error: "invalid_image" }, 400);
    }
    if (!["image/jpeg", "image/png", "image/webp"].includes(mediaType)) {
      return json({ error: "invalid_media_type" }, 400);
    }
    if (!(await consumeQuota(env, uid))) return json({ error: "daily_limit" }, 429);

    try {
      return json(await askClaude(env, image, mediaType));
    } catch (err) {
      console.error("analyze error", err);
      if (err instanceof Anthropic.RateLimitError) return json({ error: "busy" }, 503);
      return json({ error: "analyze_failed" }, 502);
    }
  },
};
