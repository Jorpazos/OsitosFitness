# Arquitectura de Ositos Fitness

## Vista general

```
┌──────────────────────── App Android (Kotlin + Compose, MVVM) ────────────────────────┐
│                                                                                     │
│  ui/screens  ──►  ViewModels  ──►  data (repositorios)  ──►  Firebase SDK            │
│  (Compose)        SessionVM        DuoRepository (Firestore, Auth, FCM token)        │
│                   DuoVM            AiRepository  (achica foto → backend IA)          │
│                     │                                                                │
│                     └─► domain (Kotlin puro, testeado): HealthCalculator, Xp,        │
│                         Levels, Streaks, Achievements, Activities (METs), Foods      │
│                                                                                     │
│  notifications: OsitosMessagingService (FCM) + DuoWorker (WorkManager, cada 30 min)  │
└─────────────────────────────────────────────────────────────────────────────────────┘
          │ tiempo real (snapshot listeners)                 │ foto JPEG ≤768px, q70
          ▼                                                  ▼
┌──────────────────────┐         ┌──────────────────────────────────────────────┐
│  Cloud Firestore     │◄────────│  Cloud Functions (southamerica-east1)        │
│  duos/main/...       │ trigger │   analyzeFood (callable) → API de Anthropic   │
│  reglas: solo los 2  │────────►│   onPokeCreated → push FCM al otro            │
└──────────────────────┘         └──────────────────────────────────────────────┘
                                   Alternativa gratis: Cloudflare Worker (solo IA)
```

- **MVVM sin Hilt**: `AppContainer` crea los repositorios; los ViewModels exponen `StateFlow`.
  Para 2 usuarios no hace falta más.
- **Todo se calcula en el celu**: XP, niveles, rachas, logros y duelos se derivan de los
  documentos diarios. Así no hace falta backend para la lógica del juego y todo es determinístico
  (no hay XP "duplicado" por reintentos).
- **Tiempo real**: ambos celulares escuchan los mismos documentos; cuando uno registra algo,
  el otro lo ve al instante (vista dividida, historial, duelo).

## Modelo de datos de Firestore

Todo vive bajo **un único dúo**: `duos/main`. El primero que entra lo crea; el segundo se agrega
solo; un tercero no puede ni leerlo.

| Ruta | Contenido | Quién escribe |
|---|---|---|
| `duos/main` | `members: [uid1, uid2]`, `createdAt`, `pokeMessages[]`, `forfeits[]` (prendas), `duelsWon{uid: n}`, `ties`, `coopDone` | Creación/unión con reglas especiales; luego los miembros (sin tocar `members`) |
| `duos/main/profiles/{uid}` | `nickname`, `avatar`, `color`, `sex`, `age`, `heightCm`, `weightKg`, `startWeightKg`, `targetWeightKg`, `activity`, `measures{waistCm, hipCm, chestCm, armCm, thighCm}`, `goalKcal`, `fcmToken`, `pokesMutedUntil`, `pokesSent`, `achievements{id: timestamp}`, `onboarded` | Solo el dueño |
| `duos/main/days/{uid}_{yyyy-MM-dd}` | `uid`, `dayKey`, `goalKcal`, `meals`, `exercises`, `water`, `weighed`, `kcalIn`, `kcalOut` (contadores con `increment`) | Solo el dueño |
| `duos/main/weights/{uid}_{yyyy-MM-dd}` | `uid`, `dayKey`, `kg` (uno por día) | Solo el dueño |
| `duos/main/logs/{autoId}` | Línea de tiempo: `uid`, `type` (MEAL, EXERCISE, WEIGHT, POKE, ACHIEVEMENT, DUEL), `ts`, `dayKey`, `title`, `detail`, `emoji`, `kcal`, `weightKg`, `reactions{uid: emoji}` | Crea el dueño; el otro solo puede tocar **su** reacción |
| `duos/main/weeks/{yyyy-Www}` | Duelo y desafío: `forfeit`, `forfeitBy`, `coopType`, `coopTarget`, `closed`, `winnerUid`, `xp{uid: n}`, `coopAchieved` | Miembros (el cierre es una transacción idempotente) |
| `duos/main/pokes/{autoId}` | `from`, `to`, `fromName`, `message`, `ts`, `delivered` | Crea quien pincha; solo el destinatario marca `delivered` |
| `duos/main/aiUsage/{uid}_{día}` | `count` de fotos analizadas hoy | Solo la Cloud Function |

No se necesitan índices compuestos: todas las consultas son por un solo campo o igualdades.

## Reglas del juego

- **XP por hábitos (no por kilos)**: comida +10 (máx. 5/día), ejercicio +20 (máx. 2/día),
  pesarse +15, vaso de agua +2 (máx. 8), cumplir meta de kcal +30.
- **Meta de kcal cumplida**: al menos 2 comidas, entre el 60 % de la meta y meta + ejercicio.
- **Día cumplido**: al menos 1 comida y 40 XP. Suma a la racha individual.
- **Racha de pareja**: días que cumplieron **los dos**. Si uno falla, se corta para ambos.
- **Duelo semanal** (lunes a domingo): gana quien junta más XP. El primer celu que abre la app
  la semana siguiente lo cierra en una transacción (suma la victoria y publica el resultado).
- **Pinchazos**: máximo 3 por día (app + Cloud Function), el destinatario puede silenciarlos.

## Seguridad de salud (no negociable)

- Meta diaria nunca por debajo de 1200 kcal (mujer) / 1500 kcal (hombre).
- Déficit máximo de 500 kcal/día (≈ 0,5 kg/semana).
- No se acepta un peso objetivo con IMC < 18,5.
- Si la tendencia de 14 días muestra una bajada > 1 kg/semana, aviso amable para consultar a un profesional.
