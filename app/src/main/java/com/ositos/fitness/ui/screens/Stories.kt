package com.ositos.fitness.ui.screens

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.ositos.fitness.data.Images
import com.ositos.fitness.data.Story
import com.ositos.fitness.ui.DuoState
import com.ositos.fitness.ui.PersonSummary
import com.ositos.fitness.ui.components.BouncyButton
import com.ositos.fitness.ui.components.GameCard
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.components.StreakFlame
import com.ositos.fitness.ui.theme.OsitoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val storyRing = Brush.sweepGradient(listOf(OsitoColors.Orange, OsitoColors.Pink, OsitoColors.Purple, OsitoColors.Orange))

/**
 * Barra de historias (arriba del Inicio): tu historia, la de tu compa y el fueguito de la racha
 * de historias. Las historias duran 24 h y solo las ven ustedes dos.
 */
@Composable
fun StoriesBar(
    s: DuoState,
    stories: List<Story>,
    uploading: Boolean,
    onAdd: () -> Unit,
    onOpen: (uid: String) -> Unit,
) {
    val me = s.me ?: return
    val live = stories.filter { !it.expired }
    val mine = live.filter { it.uid == me.profile.uid }
    val partner = s.partner
    val theirs = partner?.let { p -> live.filter { it.uid == p.profile.uid } }.orEmpty()

    GameCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StoryBubble(
                person = me,
                label = if (uploading) "Subiendo…" else "Tu historia",
                hasStories = mine.isNotEmpty(),
                unseen = false,
                showPlus = true,
                uploading = uploading,
                onClick = { if (mine.isNotEmpty()) onOpen(me.profile.uid) else onAdd() },
                onPlus = onAdd,
            )
            if (partner != null) {
                Spacer(Modifier.width(14.dp))
                StoryBubble(
                    person = partner,
                    label = partner.name,
                    hasStories = theirs.isNotEmpty(),
                    unseen = theirs.any { me.profile.uid !in it.viewedBy },
                    showPlus = false,
                    uploading = false,
                    onClick = { if (theirs.isNotEmpty()) onOpen(partner.profile.uid) },
                    onPlus = {},
                )
            }
            Spacer(Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (s.storyStreak > 0) StreakFlame(s.storyStreak, size = 26.dp) else Text("🔥", fontSize = 22.sp, color = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text("${s.storyStreak}", style = MaterialTheme.typography.titleLarge)
                    if (s.storyStreakAtRisk) Text(" ⏳", fontSize = 16.sp)
                }
                Text(
                    when {
                        s.storyStreakAtRisk && partner != null -> "¡Suban hoy!"
                        s.storyStreakAtRisk -> "¡Subí hoy!"
                        else -> "racha de fotos"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (s.storyStreakAtRisk) OsitoColors.Fire else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (partner == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Tus historias duran 24 h. Cuando armes tu dúo, solo las va a ver tu compa.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (mine.isEmpty() && theirs.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "Subí la foto del día (solo la ve ${partner.name}, dura 24 h). Si los dos suben todos los días, crece el 🔥.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StoryBubble(
    person: PersonSummary,
    label: String,
    hasStories: Boolean,
    unseen: Boolean,
    showPlus: Boolean,
    uploading: Boolean,
    onClick: () -> Unit,
    onPlus: () -> Unit,
) {
    val ctx = LocalContext.current
    val color = Color(person.profile.color)
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
        Box {
            Box(
                Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .then(
                        when {
                            hasStories && (unseen || showPlus) -> Modifier.border(3.dp, storyRing, CircleShape)
                            hasStories -> Modifier.border(3.dp, Color.Gray.copy(alpha = 0.6f), CircleShape)
                            else -> Modifier.border(2.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        },
                    )
                    .padding(5.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.22f))
                    .clickable { Haptics.tick(ctx); onClick() },
                contentAlignment = Alignment.Center,
            ) {
                if (uploading) CircularProgressIndicator(Modifier.size(26.dp), strokeWidth = 3.dp)
                else Text(person.profile.avatar, fontSize = 26.sp)
            }
            if (showPlus) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 2.dp, y = 2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(OsitoColors.Pink)
                        .border(2.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .clickable { Haptics.tick(ctx); onPlus() },
                    contentAlignment = Alignment.Center,
                ) { Text("+", color = Color.White, style = MaterialTheme.typography.labelLarge) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ------------------------------------------------------------------ Subir historia

private fun newStoryUri(context: Context): Uri {
    val dir = File(context.cacheDir, "fotos").apply { mkdirs() }
    val file = File(dir, "historia_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Subir historia: primero elegís cámara o galería (las dos a la vista), después ves la foto y publicás. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoryAddSheet(partnerName: String?, onDismiss: () -> Unit, onPublish: (Uri, String) -> Unit) {
    val ctx = LocalContext.current
    var picked by remember { mutableStateOf<Uri?>(null) }
    var pendingCamera by remember { mutableStateOf<Uri?>(null) }
    var caption by remember { mutableStateOf("") }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) picked = pendingCamera
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) picked = uri
    }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(
            Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp),
        ) {
            Text("Historia del día 📸", style = MaterialTheme.typography.headlineSmall)
            Text(
                if (partnerName != null) "Solo la ve $partnerName y se borra sola en 24 h."
                else "Se borra sola en 24 h. Cuando tengas dúo, solo la va a ver tu compa.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(14.dp))
            val uri = picked
            if (uri == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SourceOption("📷", "Tomar foto", OsitoColors.Pink, Modifier.weight(1f)) {
                        val u = newStoryUri(ctx)
                        pendingCamera = u
                        camera.launch(u)
                    }
                    SourceOption("🖼️", "Abrir galería", OsitoColors.Purple, Modifier.weight(1f)) {
                        gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }
                }
            } else {
                val preview by produceState<ImageBitmap?>(null, uri) {
                    value = withContext(Dispatchers.IO) {
                        runCatching {
                            val bytes = Images.compressJpeg(ctx, uri, 720, 80)
                            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
                        }.getOrNull()
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(340.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center,
                ) {
                    val img = preview
                    if (img == null) CircularProgressIndicator()
                    else Image(img, contentDescription = "Vista previa", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it.take(120) },
                    placeholder = { Text("Escribí algo (opcional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BouncyButton(
                        "Otra",
                        { picked = null },
                        Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    )
                    BouncyButton(
                        "Publicar",
                        { Haptics.success(ctx); onPublish(uri, caption) },
                        Modifier.weight(1.4f),
                        color = OsitoColors.Pink,
                        emoji = "🚀",
                        enabled = preview != null,
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceOption(emoji: String, title: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(color.copy(alpha = 0.18f))
            .clickable { Haptics.tick(ctx); onClick() }
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

// ------------------------------------------------------------------ Ver historias

@Composable
private fun rememberStoryImage(story: Story): ImageBitmap? {
    val img by produceState<ImageBitmap?>(null, story.id) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val bytes = Base64.decode(story.image, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return img
}

private fun timeAgo(ts: Long): String {
    val min = ((System.currentTimeMillis() - ts) / 60_000).coerceAtLeast(0)
    return when {
        min < 1 -> "recién"
        min < 60 -> "hace $min min"
        else -> "hace ${min / 60} h"
    }
}

private val storyReactions = listOf("❤️", "🔥", "😂", "😍", "👏", "💪")

/**
 * Visor a pantalla completa, estilo Instagram/Snapchat: tocá a la derecha para la siguiente,
 * a la izquierda para la anterior. Cada una dura 5 segundos.
 */
@Composable
fun StoryViewer(
    s: DuoState,
    uid: String,
    stories: List<Story>,
    onViewed: (Story) -> Unit,
    onReact: (Story, String) -> Unit,
    onDelete: (Story) -> Unit,
    onClose: () -> Unit,
) {
    val list = stories.filter { it.uid == uid && !it.expired }.sortedBy { it.ts }
    val owner = s.personOf(uid)
    val mine = uid == s.myUid
    // Arranca en la primera que no viste (si son del otro).
    var index by remember(uid) {
        mutableIntStateOf(list.indexOfFirst { !mine && s.myUid !in it.viewedBy }.takeIf { it >= 0 } ?: 0)
    }
    var confirmDelete by remember { mutableStateOf<Story?>(null) }
    var paused by remember { mutableStateOf(false) }
    if (list.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }
    val i = index.coerceIn(0, list.lastIndex)
    val story = list[i]
    val progress = remember(story.id) { Animatable(0f) }
    LaunchedEffect(story.id) { onViewed(story) }
    LaunchedEffect(story.id, paused) {
        if (paused) return@LaunchedEffect
        val left = ((1f - progress.value) * 5_000).toInt()
        progress.animateTo(1f, tween(left, easing = LinearEasing))
        if (i < list.lastIndex) index = i + 1 else onClose()
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(story.id, list.size) {
                    detectTapGestures(
                        onPress = {
                            paused = true
                            tryAwaitRelease()
                            paused = false
                        },
                        onTap = { offset ->
                            if (offset.x < size.width / 3f) {
                                index = (i - 1).coerceAtLeast(0)
                            } else if (i < list.lastIndex) {
                                index = i + 1
                            } else {
                                onClose()
                            }
                        },
                    )
                },
        ) {
            val img = rememberStoryImage(story)
            if (img == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            } else {
                Image(img, contentDescription = "Historia", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            }

            // Arriba: barritas de progreso + quién y cuándo.
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.55f), Color.Transparent)))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    list.forEachIndexed { j, _ ->
                        val f = when {
                            j < i -> 1f
                            j == i -> progress.value
                            else -> 0f
                        }
                        Box(
                            Modifier
                                .weight(1f)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color.White.copy(alpha = 0.35f)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(f)
                                    .height(3.dp)
                                    .background(Color.White),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(owner?.profile?.avatar ?: "🐻", fontSize = 24.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (mine) "Tu historia" else (owner?.name ?: "Tu compa"),
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(timeAgo(story.ts), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    Spacer(Modifier.weight(1f))
                    Text(
                        "✕",
                        color = Color.White,
                        fontSize = 22.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onClose() }
                            .padding(8.dp),
                    )
                }
            }

            // Abajo: texto, reacciones (si es del otro) o "visto" y borrar (si es tuya).
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f))))
                    .navigationBarsPadding()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                if (story.caption.isNotBlank()) {
                    Text(
                        story.caption,
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(12.dp))
                }
                if (mine) {
                    val partner = s.partner
                    val partnerUid = partner?.profile?.uid
                    val seen = partnerUid != null && partnerUid in story.viewedBy
                    val reaction = partnerUid?.let { story.reactions[it] }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            when {
                                partner == null -> "👀 Solo vos (todavía sin dúo)"
                                seen -> "👀 La vio ${partner.name}" + (reaction?.let { " · $it" } ?: "")
                                else -> "👀 ${partner.name} todavía no la vio"
                            },
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { paused = true; confirmDelete = story }) {
                            Text("🗑️ Borrar", color = Color.White)
                        }
                    }
                } else {
                    val myReaction = story.reactions[s.myUid]
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        storyReactions.forEach { e ->
                            Box(
                                Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(if (e == myReaction) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.12f))
                                    .clickable { onReact(story, e) },
                                contentAlignment = Alignment.Center,
                            ) { Text(e, fontSize = 22.sp) }
                        }
                    }
                }
            }
        }
    }

    confirmDelete?.let { st ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null; paused = false },
            title = { Text("¿Borrar esta historia?") },
            text = { Text("Se borra para los dos. La racha del día no se pierde.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    paused = false
                    onDelete(st)
                    if (list.size <= 1) onClose()
                }) { Text("Borrar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null; paused = false }) { Text("Cancelar") } },
        )
    }
}
