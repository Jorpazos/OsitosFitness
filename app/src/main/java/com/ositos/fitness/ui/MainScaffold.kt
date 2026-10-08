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
import com.ositos.fitness.ui.screens.ThemeMode
import com.ositos.fitness.ui.screens.TimelineScreen
import com.ositos.fitness.ui.screens.UsScreen
import com.ositos.fitness.ui.screens.WeightSheet
import com.ositos.fitness.ui.screens.WrappedScreen
import com.ositos.fitness.ui.theme.OsitoColors
import com.ositos.fitness.data.LogType
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
) {
    val nav = rememberNavController()
    val s by vm.state.collectAsStateWithLifecycle()
    NavHost(nav, startDestination = "main") {
        composable("main") {
            MainTabs(
                vm, s, themeMode, onThemeMode, onSignOut,
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
                    if (celebration == null) celebration = Celebration.Unlock(e.achievement) else queue.addLast(Celebration.Unlock(e.achievement))
                }
                is UiEvent.LevelUp -> {
                    if (celebration == null) celebration = Celebration.LevelUp(e.level) else queue.addLast(Celebration.LevelUp(e.level))
                }
                is UiEvent.PokeReceived -> {
                    Haptics.poke(ctx)
                    if (celebration == null) celebration = Celebration.Poked(e.fromName, e.message) else queue.addLast(Celebration.Poked(e.fromName, e.message))
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
                    onClick = { Haptics.tick(ctx); sheet = Sheet.MENU },
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
                        onWater = { vm.addWater(it) },
                        onPoke = { if (vm.sendPoke()) Haptics.poke(ctx) },
                        onOpenDuel = { tab = Tab.GAME },
                        onOpenWrapped = onOpenWrapped,
                    )
                    Tab.TIMELINE -> TimelineScreen(s, padding, onReact = vm::react, onDelete = vm::deleteLog)
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
                    )
                }
            }
        }

        // "+10 XP" flotante
        XpPopup(xpPopup, xpKey)
        ConfettiOverlay(confetti)
    }

    LogSheetHost(
        sheet = sheet,
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
            Sheet.MEAL -> MealSheet { name, kcal, detail, emoji ->
                vm.addMeal(name, kcal, detail, emoji); sheet = null
            }
            Sheet.PHOTO -> PhotoSheet(
                state = photo,
                usedToday = vm.aiUsedToday(),
                onAnalyze = vm::analyzePhoto,
                onReset = vm::resetPhoto,
                onSave = { name, kcal, detail ->
                    vm.addMeal(name, kcal, detail, "📸"); vm.resetPhoto(); sheet = null
                },
            )
            Sheet.EXERCISE -> {
                val lastActivity = s.logs.firstOrNull { it.uid == s.myUid && it.type == LogType.EXERCISE }
                    ?.let { log -> com.ositos.fitness.domain.Activities.all.firstOrNull { it.name == log.title }?.id }
                ExerciseSheet(me.profile.weightKg, lastActivity) { name, kcal, detail, emoji ->
                    vm.addExercise(name, kcal, detail, emoji); sheet = null
                }
            }
            Sheet.WEIGHT -> WeightSheet(me.weights.lastOrNull()?.kg ?: me.profile.weightKg, Color(me.profile.color)) { kg ->
                vm.addWeight(kg); sheet = null
            }
            Sheet.MENU, Sheet.MEASURES -> Unit
        }
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
            vm.sendPoke()
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
