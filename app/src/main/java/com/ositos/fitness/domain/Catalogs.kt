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

data class FoodItem(val name: String, val portion: String, val kcal: Int, val emoji: String)

/** Alimentos comunes argentinos con kcal aproximadas por porción típica. */
object Foods {
    val all = listOf(
        FoodItem("Milanesa de carne", "1 unidad (150 g)", 400, "🥩"),
        FoodItem("Milanesa de pollo", "1 unidad (150 g)", 330, "🍗"),
        FoodItem("Milanesa napolitana", "1 unidad", 600, "🍕"),
        FoodItem("Empanada de carne", "1 unidad", 290, "🥟"),
        FoodItem("Empanada de jamón y queso", "1 unidad", 270, "🥟"),
        FoodItem("Empanada de humita", "1 unidad", 250, "🥟"),
        FoodItem("Mate amargo", "1 termo", 5, "🧉"),
        FoodItem("Mate con azúcar", "1 termo (4 cdas)", 190, "🧉"),
        FoodItem("Medialuna de manteca", "1 unidad", 180, "🥐"),
        FoodItem("Medialuna de grasa", "1 unidad", 150, "🥐"),
        FoodItem("Factura con dulce de leche", "1 unidad", 250, "🥐"),
        FoodItem("Asado (tira)", "1 porción (250 g)", 700, "🍖"),
        FoodItem("Vacío", "1 porción (250 g)", 600, "🍖"),
        FoodItem("Chorizo", "1 unidad", 300, "🌭"),
        FoodItem("Choripán", "1 unidad", 480, "🌭"),
        FoodItem("Morcilla", "1 unidad", 280, "🌭"),
        FoodItem("Provoleta", "1 porción", 350, "🧀"),
        FoodItem("Pizza muzzarella", "1 porción", 280, "🍕"),
        FoodItem("Fainá", "1 porción", 160, "🍕"),
        FoodItem("Ñoquis con salsa", "1 plato", 550, "🍝"),
        FoodItem("Fideos con tuco", "1 plato", 500, "🍝"),
        FoodItem("Ravioles con salsa", "1 plato", 520, "🍝"),
        FoodItem("Tarta de verdura", "1 porción", 300, "🥧"),
        FoodItem("Tarta de jamón y queso", "1 porción", 380, "🥧"),
        FoodItem("Pastel de papa", "1 porción", 450, "🥘"),
        FoodItem("Locro", "1 plato", 550, "🍲"),
        FoodItem("Guiso de lentejas", "1 plato", 450, "🍲"),
        FoodItem("Hamburguesa casera", "1 con pan", 550, "🍔"),
        FoodItem("Lomito completo", "1 sándwich", 850, "🥪"),
        FoodItem("Sándwich de miga", "1 triple", 300, "🥪"),
        FoodItem("Tostado de jamón y queso", "1 unidad", 350, "🥪"),
        FoodItem("Ensalada mixta", "1 plato", 120, "🥗"),
        FoodItem("Ensalada César", "1 plato", 350, "🥗"),
        FoodItem("Papas fritas", "1 porción", 400, "🍟"),
        FoodItem("Puré de papas", "1 porción", 200, "🥔"),
        FoodItem("Arroz blanco", "1 taza cocida", 200, "🍚"),
        FoodItem("Pollo al horno", "1 cuarto", 400, "🍗"),
        FoodItem("Pechuga a la plancha", "150 g", 230, "🍗"),
        FoodItem("Merluza al horno", "150 g", 150, "🐟"),
        FoodItem("Huevo duro", "1 unidad", 75, "🥚"),
        FoodItem("Revuelto gramajo", "1 plato", 650, "🍳"),
        FoodItem("Alfajor de chocolate", "1 unidad", 250, "🍫"),
        FoodItem("Alfajor de maicena", "1 unidad", 180, "🍪"),
        FoodItem("Dulce de leche", "1 cucharada", 60, "🍯"),
        FoodItem("Helado", "1/4 kg", 450, "🍨"),
        FoodItem("Flan con dulce de leche", "1 porción", 350, "🍮"),
        FoodItem("Chocotorta", "1 porción", 450, "🎂"),
        FoodItem("Bizcochitos de grasa", "10 unidades", 220, "🍪"),
        FoodItem("Tostadas con queso crema", "2 tostadas", 200, "🍞"),
        FoodItem("Pan francés", "1 mignon", 140, "🥖"),
        FoodItem("Galletitas de agua", "4 unidades", 120, "🍘"),
        FoodItem("Yogur con granola", "1 pote", 250, "🥣"),
        FoodItem("Banana", "1 unidad", 100, "🍌"),
        FoodItem("Manzana", "1 unidad", 80, "🍎"),
        FoodItem("Mandarina", "1 unidad", 45, "🍊"),
        FoodItem("Café con leche", "1 taza", 110, "☕"),
        FoodItem("Cortado", "1 pocillo", 35, "☕"),
        FoodItem("Coca-Cola", "1 vaso (250 ml)", 105, "🥤"),
        FoodItem("Gaseosa light", "1 vaso", 2, "🥤"),
        FoodItem("Cerveza", "1 lata (473 ml)", 200, "🍺"),
        FoodItem("Fernet con coca", "1 vaso", 230, "🍹"),
        FoodItem("Vino tinto", "1 copa", 125, "🍷"),
        FoodItem("Jugo de naranja", "1 vaso", 110, "🧃"),
        FoodItem("Maní", "1 puñado (30 g)", 170, "🥜"),
        FoodItem("Sushi", "10 piezas", 450, "🍣"),
        FoodItem("Pancho", "1 unidad", 300, "🌭"),
        FoodItem("Churros", "2 unidades", 300, "🥖"),
        FoodItem("Garrapiñada", "1 bolsita", 250, "🥜"),
    )

    private fun norm(s: String) = Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")

    fun search(query: String): List<FoodItem> {
        if (query.isBlank()) return all
        val q = norm(query.trim())
        return all.filter { norm(it.name).contains(q) }
    }
}
