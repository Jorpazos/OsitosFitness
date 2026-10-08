# 🐻🐻 Ositos Fitness

App Android privada para **dúos** (2 personas: una pareja, dos amigas…): registran peso, comida y ejercicio,
se ven el progreso mutuamente y se motivan como en un juego cooperativo (y un poquito competitivo).
Sin culpa, con mucho XP.

> **Nombre**: el pedido decía "DuoFit" provisoriamente. Propongo **Ositos Fitness** (como el repo):
> es tierno, cómplice y da para todo el humor de la app (niveles de "Osito dormilón" a "Oso Olímpico",
> "un osito sin registrar es un osito triste"…). Si lo querés cambiar, está en
> `app/src/main/res/values/strings.xml`.

## Qué trae

| | |
|---|---|
| **Perfil y cálculos** | IMC con barra de rangos, rango saludable (IMC 18,5–24,9), peso de referencia (IMC 22), metabolismo basal (Mifflin-St Jeor), gasto diario (TDEE), meta de kcal (déficit máx. 500, mantenimiento o superávit), cintura/cadera, fecha estimada según la tendencia real de 14 días |
| **Límites de seguridad** | Nunca < 1200 kcal (mujer) / 1500 (hombre) · no deja poner objetivos con IMC < 18,5 · aviso amable si bajás > 1 kg/semana sostenido |
| **Comidas** | Buscador con ~70 comidas argentinas (milanesa, empanadas, mate, medialunas, asado, fainá, chocotorta…), carga manual, y **foto con IA** (Claude Sonnet 5.5) editable antes de guardar. Límite 20 fotos/día |
| **Ejercicio** | Kcal directas del reloj, o actividad + minutos + intensidad con METs (18 actividades) |
| **Inicio** | Anillo animado de kcal (el ejercicio suma margen), agua en 1 toque, gráfico de peso con media móvil de 7 días, vista dividida **Vos \| Tu pareja** |
| **Juego** | XP por hábitos, 12 niveles, racha individual y **racha de pareja**, duelo semanal con **prenda**, **pinchazos** con push (máx. 3/día, silenciables), alertas automáticas a la noche, 19 logros con animación Lottie, desafíos cooperativos, reacciones 👏🔥💪, resumen semanal tipo **wrapped** |
| **Para 2** | Apodo, avatar y color propios en todos los mensajes, historial compartido tipo chat, pantalla **Nosotros** con estadísticas conjuntas |
| **Diseño** | Material 3, modo oscuro por defecto, tipografía redondeada (Nunito), confeti, contadores que suben, barras que se llenan, vibración. Registrar algo: máx. 3 toques |

Arquitectura y modelo de datos de Firestore: [`docs/ARQUITECTURA.md`](docs/ARQUITECTURA.md).

```
app/                      App Android (Kotlin, Jetpack Compose, MVVM)
  src/main/java/.../domain   Cálculos y reglas del juego (Kotlin puro, con tests)
  src/main/java/.../data     Firestore, Auth, IA
  src/main/java/.../ui       Pantallas y ViewModels
functions/                Cloud Functions: IA de fotos + push de pinchazos
backend-alternativo/      Cloudflare Worker (IA gratis, sin plan Blaze)
firestore.rules           Reglas: solo los 2 miembros del dúo
firestore-tests/          Tests de las reglas en el emulador
.github/workflows/        Compila el APK en GitHub Actions
```

---

## Puesta en marcha (paso a paso)

Necesitás: una cuenta de Google, una de GitHub, y una computadora con [Node.js 22](https://nodejs.org)
para subir el backend (no hace falta Android Studio).

### 1. Crear el proyecto de Firebase

1. Entrá a <https://console.firebase.google.com> → **Crear un proyecto** (ej. `ositos-fitness`).
   Google Analytics: podés desactivarlo.
2. **Agregar app → Android**:
   - Nombre del paquete: `com.ositos.fitness`
   - Certificado de firma SHA-1 (es el del keystore de debug que viene en el repo):
     ```
     38:A1:3A:12:4B:AD:5C:6B:C2:C7:FF:F4:B9:5F:DD:E1:DC:1D:94:2C
     ```
   - Después, en **Configuración del proyecto → Tus apps → Agregar huella digital**, sumá también el SHA-256:
     ```
     B2:DE:00:12:F5:76:84:EE:26:3C:0D:68:BC:1F:95:8B:E8:BB:DF:87:40:60:29:F7:46:FA:9E:BA:0A:6B:EB:DE
     ```
     (También aparecen en el resumen de cada build de GitHub Actions.)
3. **Descargá `google-services.json`** (lo vas a usar en el paso 5). Los pasos de "agregar el SDK" saltealos: ya están hechos.
4. **Authentication** → Comenzar → **Google** → Habilitar → elegí un mail de soporte → Guardar.
5. **Firestore Database** → Crear base de datos → **modo producción** → ubicación
   **`southamerica-east1` (São Paulo)**.
   > Si elegís otra ubicación, cambiá `REGION` en `functions/index.js` y `FUNCTIONS_REGION` en
   > `app/build.gradle.kts` para que coincidan.

### 2. Subir las reglas de seguridad

```bash
npm install -g firebase-tools
firebase login
# En la carpeta del repo: poné tu Project ID en .firebaserc (reemplazá REEMPLAZAR-con-tu-project-id)
firebase deploy --only firestore:rules
```

(Alternativa sin CLI: copiá el contenido de `firestore.rules` en Firestore → Reglas → Publicar.)

🔒 **Recomendado**: en `firestore.rules`, función `emailAllowed()`, cambiá `return true;` por
`return request.auth.token.email in ['vos@gmail.com', 'tu.pareja@gmail.com'];` y volvé a desplegar.
Así ni siquiera un tercero con el APK puede "ganarle de mano" a tu pareja al unirse.

### 3. Backend de IA (elegí una opción)

La API key de Anthropic **nunca** va dentro del APK: vive solo en el backend.
Sacá una key en <https://console.anthropic.com> → API Keys (cargá unos pocos dólares de crédito:
con 2 personas y Sonnet 5.5 cada foto cuesta menos de medio centavo de dólar).

#### Opción A (recomendada): Cloud Functions — incluye las notificaciones push de los pinchazos

1. En Firebase pasá al **plan Blaze** (pago por uso). Con 2 personas el costo es prácticamente cero
   (queda dentro de la capa gratuita). Recomendado: **Uso y facturación → Detalles y configuración →
   Crear alerta de presupuesto** de, por ejemplo, USD 1.
2. Guardá la API key como secreto y desplegá:
   ```bash
   firebase functions:secrets:set ANTHROPIC_API_KEY     # pegá la key cuando la pida
   cd functions && npm ci && cd ..
   firebase deploy --only functions
   ```
   Se crean `analyzeFood` (fotos con IA) y `onPokeCreated` (push de pinchazos).

#### Opción B (gratis, sin Blaze): Cloudflare Workers

1. Creá una cuenta gratis en <https://dash.cloudflare.com>.
2. En `backend-alternativo/cloudflare-worker/wrangler.toml` poné tu `FIREBASE_PROJECT_ID`
   (y opcionalmente sus mails en `ALLOWED_EMAILS`).
3. Desplegá:
   ```bash
   cd backend-alternativo/cloudflare-worker
   npm ci
   npx wrangler login
   npx wrangler secret put ANTHROPIC_API_KEY
   npx wrangler deploy        # te muestra la URL, ej. https://ositos-ai.tu-usuario.workers.dev
   ```
   (Opcional) Límite de 20 fotos/día del lado del servidor: `npx wrangler kv namespace create USAGE`
   y descomentá el bloque `[[kv_namespaces]]` con el id.
4. En `gradle.properties` del repo:
   ```properties
   ositos.aiBackend=worker
   ositos.workerUrl=https://ositos-ai.tu-usuario.workers.dev
   ```
5. **Pinchazos sin Blaze**: sin Cloud Functions no hay push instantáneo. La app igual los muestra:
   al instante si está abierta, y si no, el trabajo en segundo plano (WorkManager) los revisa cada
   ~30 minutos y muestra la notificación. Las alertas nocturnas ("no registró nada hoy") funcionan
   igual en las dos opciones porque las genera cada celular.

### 4. Subir el código a GitHub

Si todavía no está en tu GitHub:

```bash
git remote add origin https://github.com/TU_USUARIO/OsitosFitness.git   # repo PRIVADO
git push -u origin main
```

### 5. Cargar `google-services.json` como secreto de GitHub

En GitHub: **Settings → Secrets and variables → Actions → New repository secret**

- Name: `GOOGLE_SERVICES_JSON`
- Secret: pegá **todo el contenido** del archivo `google-services.json`.

(Alternativa si el repo es **privado**: commitear el archivo en `app/google-services.json`. No es una contraseña —la seguridad la dan las reglas y el SHA-1— y el workflow lo usa si no hay secreto. Así está configurado este repo.)

Sin este secreto el APK compila igual, pero al abrirlo muestra "Falta configurar Firebase".

### 6. Compilar y descargar el APK

1. GitHub → pestaña **Actions** → workflow **Build APK** → **Run workflow** (o simplemente hacé un push).
2. Cuando termine (≈ 5–8 min), entrá a la ejecución y abajo, en **Artifacts**, descargá
   **`ositos-fitness-apk`** (un `.zip`).

### 7. Instalar en Android (los dos celulares)

1. Pasá el `.zip` al celu (o descargalo desde el navegador del celu con tu sesión de GitHub) y descomprimilo.
2. Tocá el `.apk`. Android te va a pedir **permitir instalar apps de esta fuente**: aceptá.
   Si Play Protect avisa "app desconocida", elegí *Instalar de todas formas* (es tu propia app).
3. Abrí **Ositos Fitness** → *Entrar con Google* → elegí apodo, avatar y color → cargá tus datos.
4. Tu pareja hace lo mismo con **su** cuenta de Google: se une sola al dúo. ¡Listo! 🎉

Para actualizar: descargá el APK nuevo e instalalo encima (los datos están en la nube y el keystore
fijo permite actualizar sin desinstalar).

---

## Preguntas y problemas comunes

| Problema | Solución |
|---|---|
| Al entrar con Google sale un error / "developer error" / código 10 | Falta el SHA-1 (y SHA-256) en la app Android de Firebase, o el `google-services.json` es de antes de agregarlo: descargalo de nuevo y actualizá el secreto. |
| "Falta configurar Firebase" | No está el secreto `GOOGLE_SERVICES_JSON` (paso 5). |
| "No encontramos ese PIN" / "ya está en un dúo" | Revisen el PIN (6 caracteres, sin 0/O ni 1/I). Cada persona puede estar en un solo dúo. |
| La foto con IA falla | Revisá que el backend esté desplegado (`firebase functions:log`), que el secreto tenga la key correcta y que tengas crédito en Anthropic. |
| No llegan los pinchazos | Permitir notificaciones a la app; con la opción B pueden tardar hasta ~30 min si la app está cerrada. Algunas marcas (Xiaomi, Huawei…) matan los procesos en segundo plano: excluí la app de la optimización de batería. |

## Costos

- Firebase plan Spark (gratis): Auth + Firestore + Messaging sobran para 2 personas.
- Cloud Functions (Blaze): capa gratuita de 2 M invocaciones/mes; en la práctica, USD 0.
- IA: Claude Sonnet 5.5, imagen de 768 px y máx. 300 tokens de respuesta: ~US$0,005 por foto como
  mucho (≈ US$1/mes con 6 fotos diarias entre los dos). El límite de 20 fotos/día por persona evita sorpresas.
  Para abaratar ~20 veces, cambiá `MODEL` a `claude-haiku-5-5` en `functions/index.js` (y `thinking` a `disabled`).
- Cloudflare Workers (opción B): plan Free (100.000 requests/día).

## Para desarrolladores

```bash
./gradlew testDebugUnitTest assembleDebug     # tests de dominio + APK (requiere Android SDK)
cd firestore-tests && npm ci && npm test      # reglas de Firestore en el emulador (requiere Java)
cd functions && npm ci && npm run check       # chequeo de las Cloud Functions
```

Notas de diseño y decisiones:

- **Keystore de debug versionado** (`app/debug.keystore`, contraseña `android`): así el SHA-1 no
  cambia entre builds de GitHub Actions (si cambiara, el login de Google dejaría de andar y no se
  podría actualizar el APK encima). Es aceptable para una app privada distribuida por APK; si el
  repo es público, generá uno propio con `keytool` y actualizá el SHA-1 en Firebase.
- **Sin Play Store ni panel de admin**, a propósito.
- **La lógica del juego corre en el celular** (XP, rachas, logros, duelos) a partir de contadores
  diarios en Firestore: es determinística y no necesita backend.
- **Fuentes**: Nunito (SIL Open Font License, ver `licenses/NUNITO_OFL.txt`).
