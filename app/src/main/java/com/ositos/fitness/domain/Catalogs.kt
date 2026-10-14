package com.ositos.fitness.domain

import java.text.Normalizer

enum class Intensity(val label: String) { LOW("Suave"), MEDIUM("Media"), HIGH("Fuerte") }

/** METs según el Compendium of Physical Activities (valores aproximados). */
data class ActivityType(
    val id: String,
    val name: String,
    val emoji: String,
    val metLow: Double,
    val metMedium: Double,
    val metHigh: Double,
    val hint: String = "",
) {
    fun met(i: Intensity) = when (i) {
        Intensity.LOW -> metLow
        Intensity.MEDIUM -> metMedium
        Intensity.HIGH -> metHigh
    }
}

object Activities {
    val all = listOf(
        ActivityType("caminar", "Caminar", "🚶", 3.0, 3.8, 5.0, "4 / 5,5 / 6,5 km/h"),
        ActivityType("correr", "Correr", "🏃", 8.3, 9.8, 11.8, "8 / 10 / 12 km/h"),
        ActivityType("trote", "Trote suave", "🏃‍♀️", 6.0, 7.0, 8.0, "Ritmo charla"),
        ActivityType("bici", "Bici", "🚴", 4.0, 6.8, 10.0, "Paseo / moderada / intensa"),
        ActivityType("spinning", "Spinning", "🚲", 6.8, 8.5, 10.5),
        ActivityType("pesas", "Pesas / gym", "🏋️", 3.5, 5.0, 6.0),
        ActivityType("funcional", "Funcional / HIIT", "🔥", 6.0, 8.0, 10.0),
        ActivityType("futbol", "Fútbol", "⚽", 5.0, 7.0, 10.0, "Picado / partido / competitivo"),
        ActivityType("padel", "Pádel / tenis", "🎾", 5.0, 6.5, 8.0),
        ActivityType("basquet", "Básquet", "🏀", 4.5, 6.5, 8.0),
        ActivityType("natacion", "Natación", "🏊", 5.8, 8.3, 10.0),
        ActivityType("baile", "Baile", "💃", 4.5, 6.0, 7.8, "Cumbia, salsa, zumba…"),
        ActivityType("escaleras", "Escaleras", "🪜", 4.0, 8.8, 10.0),
        ActivityType("yoga", "Yoga / pilates", "🧘", 2.5, 3.0, 4.0),
        ActivityType("soga", "Saltar la soga", "🪢", 8.8, 11.8, 12.3),
        ActivityType("limpieza", "Limpieza a fondo", "🧹", 2.5, 3.5, 4.0, "Sí, cuenta"),
        ActivityType("jardin", "Jardinería", "🌱", 3.0, 3.8, 5.0),
        ActivityType("boxeo", "Boxeo", "🥊", 5.5, 7.8, 12.8),
    )

    fun byId(id: String) = all.firstOrNull { it.id == id }
}

/**
 * Alimento del catálogo: kcal por porción típica. Si se conoce cuánto pesa (g) o cuánto
 * mide (ml) esa porción, se puede cargar también en gramos o mililitros.
 */
data class FoodItem(
    val name: String,
    val portion: String,
    val kcal: Int,
    val emoji: String,
    val grams: Int? = null,
    val ml: Int? = null,
)

/** Alimentos comunes argentinos con kcal aproximadas por porción típica. */
object Foods {
    val all = listOf(
        FoodItem("Milanesa de carne", "1 unidad (150 g)", 400, "🥩", grams = 150),
        FoodItem("Milanesa de pollo", "1 unidad (150 g)", 330, "🍗", grams = 150),
        FoodItem("Milanesa napolitana", "1 unidad (200 g)", 600, "🍕", grams = 200),
        FoodItem("Empanada de carne", "1 unidad", 290, "🥟", grams = 100),
        FoodItem("Empanada de jamón y queso", "1 unidad", 270, "🥟", grams = 90),
        FoodItem("Empanada de humita", "1 unidad", 250, "🥟", grams = 90),
        FoodItem("Mate amargo", "1 termo", 5, "🧉"),
        FoodItem("Mate con azúcar", "1 termo (4 cdas)", 190, "🧉"),
        FoodItem("Medialuna de manteca", "1 unidad", 180, "🥐", grams = 40),
        FoodItem("Medialuna de grasa", "1 unidad", 150, "🥐", grams = 35),
        FoodItem("Factura con dulce de leche", "1 unidad", 250, "🥐", grams = 60),
        FoodItem("Asado (tira)", "1 porción (250 g)", 700, "🍖", grams = 250),
        FoodItem("Vacío", "1 porción (250 g)", 600, "🍖", grams = 250),
        FoodItem("Bife de chorizo", "1 porción (250 g)", 550, "🥩", grams = 250),
        FoodItem("Carne picada magra", "150 g cocida", 300, "🥩", grams = 150),
        FoodItem("Chorizo", "1 unidad", 300, "🌭", grams = 100),
        FoodItem("Choripán", "1 unidad", 480, "🌭"),
        FoodItem("Morcilla", "1 unidad", 280, "🌭", grams = 100),
        FoodItem("Provoleta", "1 porción", 350, "🧀", grams = 100),
        FoodItem("Pizza muzzarella", "1 porción", 280, "🍕", grams = 110),
        FoodItem("Fainá", "1 porción", 160, "🍕", grams = 70),
        FoodItem("Ñoquis con salsa", "1 plato", 550, "🍝", grams = 350),
        FoodItem("Fideos con tuco", "1 plato", 500, "🍝", grams = 350),
        FoodItem("Ravioles con salsa", "1 plato", 520, "🍝", grams = 350),
        FoodItem("Tarta de verdura", "1 porción", 300, "🥧", grams = 150),
        FoodItem("Tarta de jamón y queso", "1 porción", 380, "🥧", grams = 150),
        FoodItem("Tortilla de papas", "1 porción", 300, "🍳", grams = 150),
        FoodItem("Pastel de papa", "1 porción", 450, "🥘", grams = 300),
        FoodItem("Locro", "1 plato", 550, "🍲", grams = 400),
        FoodItem("Guiso de lentejas", "1 plato", 450, "🍲", grams = 400),
        FoodItem("Hamburguesa casera", "1 con pan", 550, "🍔"),
        FoodItem("Lomito completo", "1 sándwich", 850, "🥪"),
        FoodItem("Sándwich de miga", "1 triple", 300, "🥪"),
        FoodItem("Tostado de jamón y queso", "1 unidad", 350, "🥪"),
        FoodItem("Ensalada mixta", "1 plato", 120, "🥗", grams = 200),
        FoodItem("Ensalada César", "1 plato", 350, "🥗", grams = 250),
        FoodItem("Verduras al vapor", "1 plato", 90, "🥦", grams = 250),
        FoodItem("Papas fritas", "1 porción", 400, "🍟", grams = 130),
        FoodItem("Puré de papas", "1 porción", 200, "🥔", grams = 200),
        FoodItem("Arroz blanco", "1 taza cocida", 200, "🍚", grams = 160),
        FoodItem("Fideos cocidos (sin salsa)", "1 taza", 220, "🍝", grams = 140),
        FoodItem("Pollo al horno", "1 cuarto", 400, "🍗", grams = 250),
        FoodItem("Pechuga a la plancha", "150 g", 230, "🍗", grams = 150),
        FoodItem("Merluza al horno", "150 g", 150, "🐟", grams = 150),
        FoodItem("Atún al natural", "1 lata", 130, "🐟", grams = 120),
        FoodItem("Huevo duro", "1 unidad", 75, "🥚", grams = 50),
        FoodItem("Huevo frito", "1 unidad", 110, "🍳", grams = 50),
        FoodItem("Revuelto gramajo", "1 plato", 650, "🍳"),
        FoodItem("Queso cremoso", "1 feta gruesa", 95, "🧀", grams = 30),
        FoodItem("Palta", "1/2 unidad", 115, "🥑", grams = 70),
        FoodItem("Alfajor de chocolate", "1 unidad", 250, "🍫", grams = 55),
        FoodItem("Alfajor de maicena", "1 unidad", 180, "🍪", grams = 40),
        FoodItem("Dulce de leche", "1 cucharada", 60, "🍯", grams = 20),
        FoodItem("Helado", "1/4 kg", 450, "🍨", grams = 250),
        FoodItem("Flan con dulce de leche", "1 porción", 350, "🍮"),
        FoodItem("Chocotorta", "1 porción", 450, "🎂", grams = 120),
        FoodItem("Bizcochitos de grasa", "10 unidades", 220, "🍪", grams = 45),
        FoodItem("Tostadas con queso crema", "2 tostadas", 200, "🍞"),
        FoodItem("Pan francés", "1 mignon", 140, "🥖", grams = 50),
        FoodItem("Pan integral", "1 rebanada", 75, "🍞", grams = 30),
        FoodItem("Galletitas de agua", "4 unidades", 120, "🍘", grams = 28),
        FoodItem("Avena", "4 cucharadas", 150, "🥣", grams = 40),
        FoodItem("Yogur con granola", "1 pote", 250, "🥣", grams = 200),
        FoodItem("Yogur descremado", "1 pote", 90, "🥛", grams = 190),
        FoodItem("Leche descremada", "1 vaso", 70, "🥛", ml = 200),
        FoodItem("Leche entera", "1 vaso", 125, "🥛", ml = 200),
        FoodItem("Banana", "1 unidad", 100, "🍌", grams = 120),
        FoodItem("Manzana", "1 unidad", 80, "🍎", grams = 150),
        FoodItem("Mandarina", "1 unidad", 45, "🍊", grams = 90),
        FoodItem("Naranja", "1 unidad", 70, "🍊", grams = 150),
        FoodItem("Frutillas", "1 taza", 50, "🍓", grams = 150),
        FoodItem("Café con leche", "1 taza", 110, "☕", ml = 250),
        FoodItem("Cortado", "1 pocillo", 35, "☕", ml = 60),
        FoodItem("Café solo", "1 pocillo", 2, "☕", ml = 60),
        FoodItem("Coca-Cola", "1 vaso (250 ml)", 105, "🥤", ml = 250),
        FoodItem("Gaseosa light", "1 vaso", 2, "🥤", ml = 250),
        FoodItem("Cerveza", "1 lata (473 ml)", 200, "🍺", ml = 473),
        FoodItem("Fernet con coca", "1 vaso", 230, "🍹", ml = 300),
        FoodItem("Vino tinto", "1 copa", 125, "🍷", ml = 150),
        FoodItem("Jugo de naranja", "1 vaso", 110, "🧃", ml = 250),
        FoodItem("Agua saborizada", "1 vaso", 45, "🧃", ml = 250),
        FoodItem("Maní", "1 puñado (30 g)", 170, "🥜", grams = 30),
        FoodItem("Sushi", "10 piezas", 450, "🍣"),
        FoodItem("Pancho", "1 unidad", 300, "🌭"),
        FoodItem("Churros", "2 unidades", 300, "🥖", grams = 80),
        FoodItem("Garrapiñada", "1 bolsita", 250, "🥜", grams = 50),
    )

    fun byName(name: String) = all.firstOrNull { it.name == name }

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")

    fun search(query: String): List<FoodItem> {
        if (query.isBlank()) return all
        val q = norm(query.trim())
        return all.filter { norm(it.name).contains(q) }
    }
}
