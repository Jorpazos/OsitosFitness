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
│  users, pins, duos/… │ trigger │   analyzeFood (callable) → API de Anthropic   │
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

Cada persona tiene una cuenta con un **PIN propio** de 6 caracteres. Poniendo el PIN del otro se crea
un dúo `duos/{duoId}` con las dos personas, y todo lo del dúo vive adentro. Puede haber muchos dúos
(una pareja, dos amigas…), totalmente aislados entre sí por las reglas de seguridad.

| Ruta | Contenido | Quién escribe |
|---|---|---|
| `users/{uid}` | `uid`, `pin`, `name`, `email`, `duoId` (null hasta emparejarse) | El dueño; el compa solo puede asignarle `duoId` al emparejar, y solo si estaba libre |
| `pins/{PIN}` | `uid`, `name`, `duoId` — para buscar a alguien por PIN | El dueño lo crea; se marca con el `duoId` al emparejar |
| `duos/{duoId}` | `members: [uid1, uid2]`, `createdAt`, `pokeMessages[]`, `forfeits[]` (prendas), `duelsWon{uid: n}`, `ties`, `coopDone` | Se crea al emparejar (2 personas libres, en una sola transacción); luego los miembros, sin tocar `members` |
| `duos/{duoId}/profiles/{uid}` | `nickname`, `avatar`, `color`, `sex`, `age`, `heightCm`, `weightKg`, `startWeightKg`, `targetWeightKg`, `activity`, `measures{waistCm, hipCm, chestCm, armCm, thighCm}`, `goalKcal`, `fcmToken`, `pokesMutedUntil`, `pokesSent`, `achievements{id: timestamp}`, `onboarded` | Solo el dueño |
| `duos/{duoId}/days/{uid}_{yyyy-MM-dd}` | `uid`, `dayKey`, `goalKcal`, `meals`, `exercises`, `water`, `weighed`, `kcalIn`, `kcalOut` (contadores con `increment`) | Solo el dueño |
| `duos/{duoId}/weights/{uid}_{yyyy-MM-dd}` | `uid`, `dayKey`, `kg` (uno por día) | Solo el dueño |
| `duos/{duoId}/logs/{autoId}` | Línea de tiempo: `uid`, `type` (MEAL, EXERCISE, WEIGHT, POKE, ACHIEVEMENT, DUEL), `ts`, `dayKey`, `title`, `detail`, `emoji`, `kcal`, `weightKg`, `reactions{uid: emoji}` | Crea el dueño; el otro solo puede tocar **su** reacción |
| `duos/{duoId}/weeks/{yyyy-Www}` | Duelo y desafío: `forfeit`, `forfeitBy`, `coopType`, `coopTarget`, `closed`, `winnerUid`, `xp{uid: n}`, `coopAchieved` | Miembros (el cierre es una transacción idempotente) |
| `duos/{duoId}/pokes/{autoId}` | `from`, `to`, `fromName`, `message`, `ts`, `delivered` | Crea quien pincha; solo el destinatario marca `delivered` |
| `duos/{duoId}/aiUsage/{uid}_{día}` | `count` de fotos analizadas hoy | Solo la Cloud Function |

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

## Migración desde la versión de un solo dúo

La primera versión usaba un dúo fijo `duos/main`. Al abrir la versión con PINs, cada miembro de
`duos/main` recibe su PIN y queda asignado automáticamente a ese dúo: no se pierde nada.
