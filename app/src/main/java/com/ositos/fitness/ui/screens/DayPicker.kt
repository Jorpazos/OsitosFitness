package com.ositos.fitness.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ositos.fitness.domain.Dates
import com.ositos.fitness.ui.components.Haptics
import com.ositos.fitness.ui.theme.OsitoColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/** Cuántos días para atrás se pueden completar (por si te olvidaste de cargar algo). */
const val EDITABLE_DAYS = 7

fun isEditableDay(dayKey: String): Boolean {
    val d = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return false
    val today = LocalDate.now()
    return !d.isAfter(today) && !d.isBefore(today.minusDays(EDITABLE_DAYS - 1L))
}

/** "Hoy · jueves 22 oct", "Ayer · miércoles 21 oct", "lunes 3 mar 2025". */
fun dayTitle(dayKey: String): String {
    val d = runCatching { LocalDate.parse(dayKey) }.getOrNull() ?: return dayKey
    val today = LocalDate.now()
    val base = Dates.prettyLong(d).replaceFirstChar { it.uppercase() } + if (d.year != today.year) " ${d.year}" else ""
    return when (d) {
        today -> "Hoy · $base"
        today.minusDays(1) -> "Ayer · $base"
        else -> base
    }
}

private val shortDays = listOf("L", "M", "M", "J", "V", "S", "D")

/**
 * Tira con los últimos 7 días + calendario para ir a cualquier fecha (semanas, meses o años atrás).
 * [doneDays]: días cumplidos (epochDay), se marcan con un puntito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStrip(selected: String, doneDays: Set<Long>, accent: Color, onSelect: (String) -> Unit) {
    val ctx = LocalContext.current
    var calendar by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val selectedDate = runCatching { LocalDate.parse(selected) }.getOrDefault(today)
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dayTitle(selected),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.weight(1f),
            )
            if (selectedDate != today) {
                TextButton(onClick = { onSelect(Dates.key(today)) }) { Text("Hoy") }
            }
            TextButton(onClick = { calendar = true }) { Text("📅 Calendario") }
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
            (6 downTo 0).forEach { back ->
                val d = today.minusDays(back.toLong())
                val isSel = d == selectedDate
                val done = d.toEpochDay() in doneDays
                Column(
                    Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(if (isSel) accent else MaterialTheme.colorScheme.surfaceContainerHigh)
                        .then(if (d == today && !isSel) Modifier.border(2.dp, accent, MaterialTheme.shapes.medium) else Modifier)
                        .clickable { Haptics.tick(ctx); onSelect(Dates.key(d)) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        shortDays[d.dayOfWeek.value - 1],
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "${d.dayOfMonth}",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isSel) Color.White else MaterialTheme.colorScheme.onSurface,
                    )
                    Box(
                        Modifier
                            .padding(top = 3.dp)
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (done) (if (isSel) Color.White else OsitoColors.Good) else Color.Transparent),
                    )
                }
            }
        }
        if (selectedDate.isBefore(today.minusDays(6))) {
            Spacer(Modifier.height(6.dp))
            Text(
                "📖 Viendo un día viejo: solo lectura. Se pueden completar los últimos $EDITABLE_DAYS días.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (calendar) {
        val todayMillis = today.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= todayMillis
                override fun isSelectableYear(year: Int) = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { calendar = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { ms ->
                        onSelect(Dates.key(Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()))
                    }
                    calendar = false
                }) { Text("Ver ese día") }
            },
            dismissButton = { TextButton(onClick = { calendar = false }) { Text("Cancelar") } },
        ) {
            DatePicker(state = state, title = { Text("¿Qué día querés ver?", Modifier.padding(start = 24.dp, top = 16.dp)) })
        }
    }
}
