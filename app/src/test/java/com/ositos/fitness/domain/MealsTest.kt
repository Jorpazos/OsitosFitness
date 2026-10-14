package com.ositos.fitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

class MealsTest {

    private val huevo = Foods.byName("Huevo duro")!!
    private val coca = Foods.byName("Coca-Cola")!!

    @Test
    fun cantidadGramosYMililitros() {
        // 2 huevos = 150 kcal (y es UN registro, no dos comidas).
        assertEquals(150, Portions.kcal(huevo, FoodUnit.COUNT, 2.0))
        assertEquals(38, Portions.kcal(huevo, FoodUnit.COUNT, 0.5))
        // 100 g de huevo (50 g = 75 kcal) = 150 kcal.
        assertEquals(150, Portions.kcal(huevo, FoodUnit.GRAMS, 100.0))
        // 500 ml de Coca (250 ml = 105 kcal) = 210 kcal.
        assertEquals(210, Portions.kcal(coca, FoodUnit.ML, 500.0))
        assertEquals(listOf(FoodUnit.COUNT, FoodUnit.GRAMS), Portions.unitsFor(huevo))
        assertEquals(listOf(FoodUnit.COUNT, FoodUnit.ML), Portions.unitsFor(coca))
    }

    @Test
    fun cargaManual() {
        // Etiqueta: 380 kcal cada 100 g, comí 50 g → 190.
        assertEquals(190, Portions.manualKcal(380, FoodUnit.GRAMS, 50.0))
        assertEquals(300, Portions.manualKcal(150, FoodUnit.COUNT, 2.0))
        assertEquals(84, Portions.manualKcal(42, FoodUnit.ML, 200.0))
    }

    @Test
    fun textos() {
        assertEquals("2 × Huevo duro", Portions.title("Huevo duro", FoodUnit.COUNT, 2.0))
        assertEquals("Huevo duro", Portions.title("Huevo duro", FoodUnit.COUNT, 1.0))
        assertEquals("Huevo duro", Portions.title("Huevo duro", FoodUnit.GRAMS, 120.0))
        assertEquals("120 g", Portions.describe(huevo, FoodUnit.GRAMS, 120.0))
        assertEquals("1 unidad", Portions.describe(huevo, FoodUnit.COUNT, 1.0))
    }

    @Test
    fun pasosDeCantidad() {
        assertEquals(1.5, Portions.step(1.0, FoodUnit.COUNT, up = true), 0.001)
        assertEquals(0.5, Portions.step(0.5, FoodUnit.COUNT, up = false), 0.001)
        assertEquals(110.0, Portions.step(100.0, FoodUnit.GRAMS, up = true), 0.001)
    }

    @Test
    fun repartoPorComida() {
        assertEquals(500, MealPlan.recommended(MealSlot.BREAKFAST, 2000))
        assertEquals(700, MealPlan.recommended(MealSlot.LUNCH, 2000))
        assertEquals(300, MealPlan.recommended(MealSlot.SNACK, 2000))
        assertEquals(500, MealPlan.recommended(MealSlot.DINNER, 2000))
        assertEquals(1.0, MealSlot.entries.sumOf { it.share }, 0.0001)
    }

    @Test
    fun comidaSegunLaHora() {
        assertEquals(MealSlot.BREAKFAST, MealSlot.forTime(LocalTime.of(8, 30)))
        assertEquals(MealSlot.LUNCH, MealSlot.forTime(LocalTime.of(13, 0)))
        assertEquals(MealSlot.SNACK, MealSlot.forTime(LocalTime.of(17, 30)))
        assertEquals(MealSlot.DINNER, MealSlot.forTime(LocalTime.of(21, 0)))
        assertEquals(MealSlot.DINNER, MealSlot.forTime(LocalTime.of(1, 0)))
    }

    @Test
    fun ositoChefNoSePasa() {
        for (slot in MealSlot.entries) {
            for (seed in 0..20) {
                val idea = OsitoChef.suggest(slot, 500, seed)
                assertTrue(idea.items.isNotEmpty())
                assertTrue("$slot $seed ${idea.kcal}", idea.kcal <= 500)
            }
        }
        // Aunque no quede nada, propone algo liviano.
        assertTrue(OsitoChef.suggest(MealSlot.DINNER, -200, 1).items.isNotEmpty())
    }

    @Test
    fun imcCuantoFalta() {
        // 170 cm, 120 kg → IMC 41,5 (Obesidad III). IMC 40 = 115,6 kg: hay que quedar por debajo → 4,5 kg.
        val step = HealthCalculator.nextBmiStep(120.0, 170.0)
        assertNotNull(step)
        assertEquals("Obesidad II", step!!.next.label)
        assertEquals(4.5, step.kg, 0.001)
        assertTrue(!step.gain)
        // Sobrepeso → saludable.
        val s2 = HealthCalculator.nextBmiStep(80.0, 170.0)!!
        assertEquals("Saludable", s2.next.label)
        assertEquals(7.8, s2.kg, 0.001)
        // Bajo peso → subir hasta 18,5.
        val s3 = HealthCalculator.nextBmiStep(50.0, 170.0)!!
        assertEquals("Saludable", s3.next.label)
        assertTrue(s3.gain)
        assertEquals(3.5, s3.kg, 0.001)
        // Saludable: nada que mostrar.
        assertNull(HealthCalculator.nextBmiStep(65.0, 170.0))
    }
}
