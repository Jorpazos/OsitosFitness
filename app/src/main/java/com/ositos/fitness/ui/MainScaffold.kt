package com.ositos.fitness.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.ositos.fitness.R
import com.ositos.fitness.domain.Achievement
import com.ositos.fitness.domain.Level
import com.ositos.fitness.ui.components.ConfettiOverlay
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.screens.GameScreen
import com.ositos.fitness.ui.screens.HomeScreen
import com.ositos.fitness.ui.screens.ListEditorDialog
import com.ositos.fitness.ui.screens.LogSheetHost
import com.ositos.fitness.ui.screens.MealSheet
import com.ositos.fitness.ui.screens.MeasuresScreen
import com.ositos.fitness.ui.screens.ExerciseSheet
import com.ositos.fitness.ui.screens.PhotoSheet
import com.ositos.fitness.ui.screens.ProfileScreen
import com.ositos.fitness.ui.screens.Sheet
import com.ositos.fitness.ui.screens.StoryAddSheet
import com.ositos.fitness.ui.screens.StoryViewer
import com.ositos.fitness.ui.screens.ThemeMode
import com.ositos.fitness.ui.screens.TimelineScreen
import com.ositos.fitness.ui.screens.UsScreen
import com.ositos.fitness.ui.screens.WeightSheet
import com.ositos.fitness.ui.screens.WrappedScreen
import com.ositos.fitness.ui.theme.OsitoColors
import com.ositos.fitness.data.Account
import com.ositos.fitness.data.LogType
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.domain.MealSlot
import com.ositos.fitness.notifications.Notifications
import com.ositos.fitness.ui.screens.dayTitle
import com.ositos.fitness.ui.screens.isEditableDay
import kotlinx.coroutines.delay

private enum class Tab(val label: String, val icon: ImageVector) {
    HOME("Inicio", Icons.Default.Home),
    TIMELINE("Historial", Icons.Default.Forum),
    GAME("Juego", Icons.Default.EmojiEvents),
    US("Nosotros", Icons.Default.Favorite),
    PROFILE("Perfil", Icons.Default.Person),
}

private sealed interface Celebration {
    data class Unlock(val a: Achievement) : Celebration
    data class LevelUp(val l: Level) : Celebration
    data class Poked(val from: String, val msg: String) : Celebration
}

@Composable
fun MainScaffold(
    vm: DuoViewModel,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    account: Account? = null,
    onLeaveDuo: () -> Unit = {},
    onFindPartner: () -> Unit = {},
) {
    val nav = rememberNavController()
    val s by vm.state.collectAsStateWithLifecycle()
    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainTabs(
                vm, s, themeMode, onThemeMode, onSignOut, account, onLeaveDuo, onFindPartner,
                onOpenWrapped = { nav.navigate("wrapped") },
                onOpenMeasures = { nav.navigate("measures") },
            )
        }
        composable("measures") {
            MeasuresScreen(s, onBack = { nav.popBackStack() }, onSave = { vm.addMeasures(it) })
        }
        composable("wrapped") {
            WrappedScreen(s, onClose = { nav.popBackStack() })
        }
    }
}

@Composable
private fun MainTabs(
    vm: DuoViewModel,
    s: DuoState,
    themeMode: ThemeMode,
    onThemeMode: (ThemeMode) -> Unit,
    onSignOut: () -> Unit,
    account: Account?,
    onLeaveDuo: () -> Unit,
    onFindPartner: () -> Unit,
    onOpenWrapped: () -> Unit,
    onOpenMeasures: () -> Unit,
) {
    val ctx = LocalContext.current
    var tab by rememberSaveable { mutableStateOf(Tab.HOME) }
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    var confetti by remember { mutableIntStateOf(0) }
    var xpPopup by remember { mutableStateOf<Int?>(null) }
    var xpKey by remember { mutableIntStateOf(0) }
    var celebration by remember { mutableStateOf<Celebration?>(null) }
    val queue = remember { ArrayDeque<Celebration>() }
    var editList by remember { mutableStateOf<String?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val photo by vm.photo.collectAsStateWithLifecycle()
    val stories by vm.stories.collectAsStateWithLifecycle()
    val storyUploading by vm.storyUploading.collectAsStateWithLifecycle()
    var mealSlot by remember { mutableStateOf(MealSlot.now()) }
    var storyAdd by remember { mutableStateOf(false) }
    var storyViewer by remember { mutableStateOf<String?>(null) }
    var pokeDialog by remember { mutableStateOf(false) }
    // Día que se mira en el Inicio, y día al que va lo que se carga desde las hojas.
    var viewDay by rememberSaveable { mutableStateOf(Dates.todayKey()) }
    var sheetDay by remember { mutableStateOf(Dates.todayKey()) }
    val viewLogs by remember(viewDay) { vm.logsForDay(viewDay) }.collectAsStateWithLifecycle(emptyList())
    // Avisos que bajan desde arriba (logros, pinchazos): no frenan lo que estás haciendo.
    var banner by remember { mutableStateOf<Banner?>(null) }
    val banners = remember { ArrayDeque<Banner>() }
    fun pushBanner(b: Banner) {
        if (banner == null) banner = b else banners.addLast(b)
    }
    /** ¿Esa comida del día que se está cargando ya tenía algo? */
    fun slotHasEntries(slot: MealSlot): Boolean? =
        if (sheetDay == viewDay) viewLogs.any { it.uid == s.myUid && it.type == LogType.MEAL && it.mealSlot == slot } else null

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Toast -> snackbar.showSnackbar(e.message)
                is UiEvent.XpGained -> {
                    Haptics.success(ctx)
                    xpPopup = e.amount
                    xpKey++
                }
                is UiEvent.AchievementUnlocked -> {
                    Haptics.success(ctx)
                    confetti++
                    pushBanner(
                        Banner(
                            e.achievement.emoji, "¡Logro desbloqueado! ${e.achievement.name}",
                            e.achievement.description, OsitoColors.Purple, "Ver", { tab = Tab.GAME },
                        ),
                    )
                }
                is UiEvent.LevelUp -> {
                    if (celebration == null) celebration = Celebration.LevelUp(e.level) else queue.addLast(Celebration.LevelUp(e.level))
                }
                is UiEvent.PokeReceived -> {
                    if (e.milestone) {
                        Haptics.success(ctx)
                        confetti++
                        pushBanner(Banner("🏆", "¡${e.fromName} logró algo grande!", e.message, OsitoColors.Fire))
                    } else {
                        // 3 vibraciones fuertes + notificación con el mensaje + aviso arriba.
                        Haptics.poke(ctx)
                        val id = (e.fromName + e.message + System.currentTimeMillis()).hashCode()
                        Notifications.show(
                            ctx, id, "📌 ${e.fromName} te pinchó", e.message,
                            actions = listOf(Notifications.pokeBackAction(ctx, id)),
                        )
                        pushBanner(
                            Banner("📌", "${e.fromName} te pinchó", "“${e.message}”", OsitoColors.Pink, "Responder", { pokeDialog = true }),
                        )
                    }
                }
                UiEvent.Confetti -> confetti++
            }
        }
    }
    LaunchedEffect(celebration) {
        if (celebration is Celebration.Unlock || celebration is Celebration.LevelUp) {
            Haptics.success(ctx)
            confetti++
        }
    }

    if (s.loading || s.me == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("🐻", fontSize = 56.sp)
        }
        return
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbar) },
            floatingActionButton = {
                val rot by animateFloatAsState(if (sheet != null) 45f else 0f, spring(Spring.DampingRatioMediumBouncy), label = "fab")
                FloatingActionButton(
                    onClick = {
                        Haptics.tick(ctx)
                        mealSlot = MealSlot.now()
                        // Si estás mirando un día de la semana pasada en el Inicio, se carga en ese día.
                        sheetDay = if (tab == Tab.HOME && isEditableDay(viewDay)) viewDay else Dates.todayKey()
                        sheet = Sheet.MENU
                    },
                    containerColor = Color(s.me?.profile?.color ?: 0xFFFF7A45),
                    contentColor = Color.White,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.size(64.dp),
                ) { Icon(Icons.Default.Add, "Registrar", Modifier.size(32.dp).rotate(rot)) }
            },
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    Tab.entries.forEach { t ->
                        val selected = t == tab
                        val scale by animateFloatAsState(if (selected) 1.18f else 1f, spring(Spring.DampingRatioHighBouncy), label = "tab")
                        NavigationBarItem(
                            selected = selected,
                            onClick = { Haptics.tick(ctx); tab = t },
                            icon = { Icon(t.icon, t.label, Modifier.scale(scale)) },
                            label = { Text(t.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            },
        ) { padding ->
            AnimatedContent(
                targetState = tab,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) },
                label = "tabs",
            ) { t ->
                when (t) {
                    Tab.HOME -> HomeScreen(
                        s, padding,
                        isSolo = account?.isSolo == true,
                        onFindPartner = onFindPartner,
                        onWater = { vm.addWater(it, viewDay) },
                        onPoke = { pokeDialog = true },
                        onOpenDuel = { tab = Tab.GAME },
                        onOpenWrapped = onOpenWrapped,
                        stories = stories,
                        storyUploading = storyUploading,
                        onAddStory = { storyAdd = true },
                        onOpenStories = { storyViewer = it },
                        onAddMeal = { slot -> mealSlot = slot; sheetDay = viewDay; sheet = Sheet.MEAL },
                        onDeleteLog = { vm.deleteLog(it, viewLogs) },
                        onChefIdea = vm::addChefIdea,
                        viewDay = viewDay,
                        dayLogs = viewLogs,
                        onSelectDay = { viewDay = it },
                        onAddExercise = { sheetDay = viewDay; sheet = Sheet.EXERCISE },
                        onOpenAchievements = { tab = Tab.GAME },
                    )
                    Tab.TIMELINE -> TimelineScreen(
                        s, padding,
                        logsFor = vm::logsForDay,
                        onReact = vm::react,
                        onDelete = { e, logs -> vm.deleteLog(e, logs) },
                    )
                    Tab.GAME -> GameScreen(
                        s, padding,
                        onForfeit = vm::setForfeit,
                        onEditForfeits = { editList = "forfeits" },
                        onCoop = vm::setCoop,
                    )
                    Tab.US -> UsScreen(s, padding, onOpenWrapped)
                    Tab.PROFILE -> ProfileScreen(
                        s, padding, themeMode, onThemeMode,
                        onSave = vm::saveProfile,
                        onMute = vm::mutePokes,
                        onEditPokeMessages = { editList = "pokes" },
                        onEditForfeits = { editList = "forfeits" },
                        onSignOut = onSignOut,
                        onOpenMeasures = onOpenMeasures,
                        myPin = account?.pin,
                        isSolo = account?.isSolo == true,
                        onFindPartner = onFindPartner,
                        onDuoKind = vm::setDuoKind,
                        onLeaveDuo = onLeaveDuo,
                    )
                }
            }
        }

        // "+10 XP" flotante
        XpPopup(xpPopup, xpKey)
        ConfettiOverlay(confetti)

        TopBanner(banner) { banner = banners.removeFirstOrNull() }

        // Visor de historias a pantalla completa, por encima de todo (barra de abajo incluida).
        storyViewer?.let { uid ->
            StoryViewer(
                s = s,
                uid = uid,
                stories = stories,
                onViewed = vm::markStoryViewed,
                onReact = vm::reactStory,
                onDelete = vm::deleteStory,
                onClose = { storyViewer = null },
            )
        }
    }

    LogSheetHost(
        sheet = sheet,
        dayLabel = if (sheetDay != Dates.todayKey()) "📅 Cargando en: ${dayTitle(sheetDay)}" else null,
        onDismiss = { sheet = null; vm.resetPhoto() },
        onSelect = {
            if (it == Sheet.MEASURES) {
                sheet = null
                onOpenMeasures()
            } else {
                sheet = it
            }
        },
    ) { current ->
        val me = s.me ?: return@LogSheetHost
        when (current) {
            Sheet.MEAL -> MealSheet(
                initialSlot = mealSlot,
                onAdd = { vm.addMeal(it.name, it.kcal, it.detail, it.emoji, it.slot, sheetDay, slotHasEntries(it.slot)) },
                onDone = { sheet = null },
            )
            Sheet.PHOTO -> PhotoSheet(
                state = photo,
                usedToday = vm.aiUsedToday(),
                initialSlot = mealSlot,
                onAnalyze = vm::analyzePhoto,
                onAnalyzeText = vm::analyzeText,
                onReset = vm::resetPhoto,
                onSave = { name, kcal, detail, slot ->
                    vm.addMeal(name, kcal, detail, "🤖", slot, sheetDay, slotHasEntries(slot)); vm.resetPhoto(); sheet = null
                },
            )
            Sheet.EXERCISE -> {
                val lastActivity = s.logs.firstOrNull { it.uid == s.myUid && it.type == LogType.EXERCISE }
                    ?.let { log -> com.ositos.fitness.domain.Activities.all.firstOrNull { it.name == log.title }?.id }
                ExerciseSheet(me.profile.weightKg, lastActivity) { name, kcal, detail, emoji ->
                    vm.addExercise(name, kcal, detail, emoji, sheetDay); sheet = null
                }
            }
            Sheet.WEIGHT -> WeightSheet(me.weights.lastOrNull()?.kg ?: me.profile.weightKg, Color(me.profile.color)) { kg ->
                vm.addWeight(kg); sheet = null
            }
            Sheet.MENU, Sheet.MEASURES -> Unit
        }
    }

    if (pokeDialog) {
        PokeDialog(
            s = s,
            onDismiss = { pokeDialog = false },
            onSend = { msg ->
                if (vm.sendPoke(msg)) Haptics.poke(ctx)
                pokeDialog = false
            },
        )
    }

    if (storyAdd) {
        StoryAddSheet(
            partnerName = s.partner?.name,
            onDismiss = { storyAdd = false },
            onPublish = { uri, caption -> vm.postStory(uri, caption); storyAdd = false },
        )
    }

    editList?.let { which ->
        val duo = s.duo
        if (which == "pokes") {
            ListEditorDialog("Mensajes de pinchazo", duo?.pokeMessages ?: emptyList(), { editList = null }) {
                vm.updatePokeMessages(it); editList = null
            }
        } else {
            ListEditorDialog("Prendas", duo?.forfeits ?: emptyList(), { editList = null }) {
                vm.updateForfeits(it); editList = null
            }
        }
    }

    celebration?.let { c ->
        CelebrationDialog(c, onDismiss = { celebration = queue.removeFirstOrNull() }, onPokeBack = {
            pokeDialog = true
            celebration = queue.removeFirstOrNull()
        })
    }
}

@Composable
private fun XpPopup(amount: Int?, key: Int) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(key) {
        if (amount != null && key > 0) {
            visible = true
            delay(1300)
            visible = false
        }
    }
    Box(Modifier.fillMaxSize().statusBarsPadding().padding(top = 24.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            Text(
                "+${amount ?: 0} XP ⭐",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(OsitoColors.Purple)
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun CelebrationDialog(c: Celebration, onDismiss: () -> Unit, onPokeBack: () -> Unit) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(R.raw.achievement))
    val progress by animateLottieCompositionAsState(composition, iterations = 1)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            if (c is Celebration.Poked) {
                TextButton(onClick = onPokeBack) { Text("Pincharle de vuelta 📌") }
            }
            TextButton(onClick = onDismiss) { Text(if (c is Celebration.Poked) "Ok, ok 😅" else "¡Vamos! 🎉") }
        },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                when (c) {
                    is Celebration.Unlock -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                            LottieAnimation(composition, { progress }, Modifier.fillMaxSize())
                            Text(c.a.emoji, fontSize = 52.sp)
                        }
                        Text("¡Logro desbloqueado!", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(c.a.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text(c.a.description, textAlign = TextAlign.Center)
                    }
                    is Celebration.LevelUp -> {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(170.dp)) {
                            LottieAnimation(composition, { progress }, Modifier.fillMaxSize())
                            Text(c.l.emoji, fontSize = 52.sp)
                        }
                        Text("¡Subiste de nivel!", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text("Nivel ${c.l.number}", style = MaterialTheme.typography.displaySmall)
                        Text(c.l.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    }
                    is Celebration.Poked -> {
                        val wobble by animateFloatAsState(if (progress > 0.5f) -8f else 8f, tween(200), label = "w")
                        Text("📌", fontSize = 64.sp, modifier = Modifier.rotate(wobble))
                        Spacer(Modifier.height(4.dp))
                        Text("${c.from} te pinchó", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                        Text("“${c.msg}”", style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                    }
                }
            }
        },
    )
}

/** Aviso que baja desde arriba unos segundos (logros, pinchazos). No frena lo que estás haciendo. */
data class Banner(
    val emoji: String,
    val title: String,
    val body: String,
    val color: Color,
    val actionLabel: String? = null,
    val action: (() -> Unit)? = null,
)

@Composable
private fun TopBanner(b: Banner?, onDone: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(b) {
        if (b != null) {
            visible = true
            delay(4_500)
            visible = false
            delay(350)
            onDone()
        }
    }
    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .padding(top = 8.dp, start = 12.dp, end = 12.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible && b != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val bb = b ?: return@AnimatedVisibility
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(10.dp, RoundedCornerShape(22.dp))
                    .clip(RoundedCornerShape(22.dp))
                    .background(Brush.horizontalGradient(listOf(bb.color, bb.color.copy(alpha = 0.78f))))
                    .clickable { visible = false }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(bb.emoji, fontSize = 32.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(bb.title, style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Text(bb.body, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.92f), maxLines = 2)
                }
                if (bb.actionLabel != null) {
                    TextButton(onClick = { bb.action?.invoke(); visible = false }) {
                        Text(bb.actionLabel, color = Color.White, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }
        }
    }
}

/** Pinchazo: escribís el mensaje (o elegís uno de la lista) y listo. Le vibra 3 veces fuerte. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PokeDialog(s: DuoState, onDismiss: () -> Unit, onSend: (String) -> Unit) {
    val partner = s.partner
    var msg by remember { mutableStateOf("") }
    val left = 3 - s.pokesSentToday
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("📌 Pinchar a ${partner?.name ?: "tu compa"}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "Le llega una notificación con tu mensaje y el celu le vibra 3 veces. Te quedan $left hoy.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = msg,
                    onValueChange = { msg = it.take(120) },
                    placeholder = { Text("Escribí tu mensaje…") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    (s.duo?.pokeMessages ?: emptyList()).take(6).forEach { m ->
                        SuggestionChip(onClick = { msg = m }, label = { Text(m, maxLines = 1) })
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSend(msg.trim()) }, enabled = msg.isNotBlank() && left > 0 && partner != null) {
                Text("Enviar 📌", fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
