package com.ositos.fitness.domain

import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Las 4 comidas del día. [share] es la parte de la meta diaria que se recomienda para cada una
 * (25 / 35 / 15 / 25 %), un reparto clásico de nutrición.
 */
enum class MealSlot(val label: String, val emoji: String, val share: Double) {
    BREAKFAST("Desayuno", "☕", 0.25),
    LUNCH("Almuerzo", "🍝", 0.35),
    SNACK("Merienda", "🧉", 0.15),
    DINNER("Cena", "🍲", 0.25);

    companion object {
        fun fromName(name: String?): MealSlot? = entries.firstOrNull { it.name == name }

        /** La comida que corresponde a esa hora (para registros viejos y para elegir por defecto). */
        fun forTime(time: LocalTime): MealSlot = when (time.hour) {
            in 4..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 16..19 -> SNACK
            else -> DINNER
        }

        fun forTimestamp(ts: Long): MealSlot =
            forTime(Instant.ofEpochMilli(ts).atZone(ZoneId.systemDefault()).toLocalTime())

        fun now(): MealSlot = forTime(LocalTime.now())
    }
}

/** Cómo se carga la cantidad: unidades/porciones, gramos o mililitros. */
enum class FoodUnit(val label: String, val short: String, val step: Double, val min: Double) {
    COUNT("Cantidad", "u.", 0.5, 0.5),
    GRAMS("Gramos", "g", 10.0, 5.0),
    ML("Mililitros", "ml", 50.0, 10.0),
}

object Portions {

    /** Unidades disponibles para un alimento del catálogo. */
    fun unitsFor(f: FoodItem): List<FoodUnit> = buildList {
        add(FoodUnit.COUNT)
        if (f.grams != null) add(FoodUnit.GRAMS)
        if (f.ml != null) add(FoodUnit.ML)
    }

    /** Cantidad inicial al elegir una unidad: 1 porción, o lo que pesa/mide una porción. */
    fun defaultAmount(f: FoodItem, unit: FoodUnit): Double = when (unit) {
        FoodUnit.COUNT -> 1.0
        FoodUnit.GRAMS -> (f.grams ?: 100).toDouble()
        FoodUnit.ML -> (f.ml ?: 100).toDouble()
    }

    /** kcal de una cantidad de un alimento del catálogo. */
    fun kcal(f: FoodItem, unit: FoodUnit, amount: Double): Int = when (unit) {
        FoodUnit.COUNT -> f.kcal * amount
        FoodUnit.GRAMS -> f.grams?.let { f.kcal * amount / it } ?: 0.0
        FoodUnit.ML -> f.ml?.let { f.kcal * amount / it } ?: 0.0
    }.roundToInt().coerceAtLeast(0)

    /**
     * kcal de una carga manual: en "Cantidad" el dato es kcal por unidad; en gramos o
     * mililitros, kcal cada 100 g / 100 ml (como dicen las etiquetas).
     */
    fun manualKcal(kcalPerBase: Int, unit: FoodUnit, amount: Double): Int = when (unit) {
        FoodUnit.COUNT -> kcalPerBase * amount
        FoodUnit.GRAMS, FoodUnit.ML -> kcalPerBase * amount / 100.0
    }.roundToInt().coerceAtLeast(0)

    fun step(amount: Double, unit: FoodUnit, up: Boolean): Double {
        val next = if (up) amount + unit.step else amount - unit.step
        return next.coerceIn(unit.min, 5000.0)
    }

    /** "1,5", "2", "250" — sin decimales de más. */
    fun fmt(amount: Double): String =
        if (abs(amount - amount.roundToInt()) < 0.01) amount.roundToInt().toString()
        else "%.1f".format(amount)

    /** Texto de la cantidad para el historial: "2 × 1 unidad", "300 g", "500 ml". */
    fun describe(f: FoodItem?, unit: FoodUnit, amount: Double): String = when (unit) {
        FoodUnit.COUNT -> {
            val portion = f?.portion ?: "unidad"
            if (abs(amount - 1.0) < 0.01) portion else "${fmt(amount)} × $portion"
        }
        FoodUnit.GRAMS -> "${fmt(amount)} g"
        FoodUnit.ML -> "${fmt(amount)} ml"
    }

    /** Título para el diario: "2 × Huevo duro" cuando son varias unidades. */
    fun title(name: String, unit: FoodUnit, amount: Double): String =
        if (unit == FoodUnit.COUNT && abs(amount - 1.0) >= 0.01) "${fmt(amount)} × $name" else name
}

/** kcal recomendadas para cada comida según la meta diaria (que sale de tu peso, altura, edad y objetivo). */
object MealPlan {
    fun recommended(slot: MealSlot, dailyBudget: Int): Int =
        ((dailyBudget * slot.share) / 10.0).roundToInt() * 10
}

/**
 * Osito Chef 🎲: propone una comida del catálogo que entre en las kcal que te quedan.
 * Siempre sugiere algo razonable para el momento del día (nada de asado en el desayuno).
 */
object OsitoChef {

    data class Idea(val items: List<FoodItem>) {
        val kcal get() = items.sumOf { it.kcal }
    }

    private val mains = mapOf(
        MealSlot.BREAKFAST to listOf(
            "Yogur con granola", "Tostadas con queso crema", "Avena", "Medialuna de manteca",
            "Huevo duro", "Pan integral", "Yogur descremado",
        ),
        MealSlot.LUNCH to listOf(
            "Pechuga a la plancha", "Merluza al horno", "Milanesa de pollo", "Tarta de verdura",
            "Fideos con tuco", "Guiso de lentejas", "Bife de chorizo", "Tortilla de papas", "Atún al natural",
        ),
        MealSlot.SNACK to listOf(
            "Yogur descremado", "Banana", "Manzana", "Tostadas con queso crema", "Frutillas",
            "Galletitas de agua", "Mandarina", "Maní",
        ),
        MealSlot.DINNER to listOf(
            "Merluza al horno", "Pechuga a la plancha", "Tarta de verdura", "Tortilla de papas",
            "Pollo al horno", "Pizza muzzarella", "Empanada de carne", "Verduras al vapor",
        ),
    )

    private val sides = mapOf(
        MealSlot.BREAKFAST to listOf("Café con leche", "Cortado", "Banana", "Mandarina", "Frutillas", "Mate amargo"),
        MealSlot.LUNCH to listOf("Ensalada mixta", "Puré de papas", "Arroz blanco", "Verduras al vapor", "Manzana"),
        MealSlot.SNACK to listOf("Mate amargo", "Café con leche", "Cortado", "Leche descremada"),
        MealSlot.DINNER to listOf("Ensalada mixta", "Verduras al vapor", "Puré de papas", "Mandarina", "Frutillas"),
    )

    /**
     * Arma una idea que no se pase de [kcalLeft] (con un mínimo razonable por si ya no queda
     * nada: siempre conviene comer algo liviano). [seed] cambia la idea en cada "otra idea".
     */
    fun suggest(slot: MealSlot, kcalLeft: Int, seed: Int): Idea {
        val budget = kcalLeft.coerceAtLeast(150)
        val mainOptions = mains.getValue(slot).mapNotNull { Foods.byName(it) }
        val sideOptions = sides.getValue(slot).mapNotNull { Foods.byName(it) }
        val fitting = mainOptions.filter { it.kcal <= budget }.ifEmpty { listOf(mainOptions.minBy { it.kcal }) }
        val rot = abs(seed)
        val main = fitting[rot % fitting.size]
        val items = mutableListOf(main)
        var left = budget - main.kcal
        // Hasta dos acompañamientos que entren.
        val sideOrder = sideOptions.indices.sortedBy { (it * 5 + rot) % sideOptions.size }.map { sideOptions[it] }
        for (side in sideOrder) {
            if (items.size >= 3) break
            if (side.kcal <= left && side != main) {
                items += side
                left -= side.kcal
            }
        }
        return Idea(items)
    }
}
