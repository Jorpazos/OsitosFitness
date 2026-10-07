package com.ositos.fitness.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HealthCalculatorTest {

    @Test
    fun bmi() {
        assertEquals(22.86, HealthCalculator.bmi(70.0, 175.0), 0.01)
        assertEquals("Saludable", HealthCalculator.bmiCategory(22.86).label)
        assertEquals("Sobrepeso", HealthCalculator.bmiCategory(27.0).label)
        assertEquals("Bajo peso", HealthCalculator.bmiCategory(18.0).label)
    }

    @Test
    fun healthyRangeAndReference() {
        val r = HealthCalculator.healthyRange(170.0)
        assertEquals(53.47, r.start, 0.01)
        assertEquals(71.96, r.endInclusive, 0.01)
        assertEquals(63.58, HealthCalculator.referenceWeight(170.0), 0.01)
    }

    @Test
    fun mifflinStJeor() {
        // Hombre 30 años, 80 kg, 180 cm: 10*80 + 6.25*180 - 5*30 + 5 = 1780
        assertEquals(1780.0, HealthCalculator.bmr(Sex.MALE, 80.0, 180.0, 30), 0.001)
        // Mujer 30 años, 65 kg, 165 cm: 650 + 1031.25 - 150 - 161 = 1370.25
        assertEquals(1370.25, HealthCalculator.bmr(Sex.FEMALE, 65.0, 165.0, 30), 0.001)
        assertEquals(1780.0 * 1.55, HealthCalculator.tdee(1780.0, ActivityLevel.MODERATE), 0.001)
    }

    @Test
    fun goalKcalModerateDeficitAndFloors() {
        assertEquals(2259, HealthCalculator.dailyGoalKcal(Sex.MALE, 2759.0, GoalType.LOSE))
        assertEquals(2759, HealthCalculator.dailyGoalKcal(Sex.MALE, 2759.0, GoalType.MAINTAIN))
        assertEquals(3059, HealthCalculator.dailyGoalKcal(Sex.MALE, 2759.0, GoalType.GAIN))
        // Pisos de seguridad
        assertEquals(1200, HealthCalculator.dailyGoalKcal(Sex.FEMALE, 1500.0, GoalType.LOSE))
        assertEquals(1500, HealthCalculator.dailyGoalKcal(Sex.MALE, 1800.0, GoalType.LOSE))
    }

    @Test
    fun targetWeightNeverBelowBmi185() {
        assertFalse(HealthCalculator.isTargetAllowed(50.0, 170.0)) // IMC 17.3
        assertTrue(HealthCalculator.isTargetAllowed(HealthCalculator.minTargetWeight(170.0), 170.0))
        assertEquals(53.5, HealthCalculator.minTargetWeight(170.0), 0.001)
    }

    @Test
    fun waistHip() {
        val r = HealthCalculator.waistHipRatio(Measures(waistCm = 80.0, hipCm = 100.0))
        assertEquals(0.8, r!!, 0.0001)
        assertNull(HealthCalculator.waistHipRatio(Measures(waistCm = 80.0)))
    }

    @Test
    fun etaFromTrend() {
        // Baja 0.1 kg/día durante 14 días → 0.7 kg/semana
        val today = 20_000L
        val pts = (0..13).map { WeightPoint(today - 13 + it, 80.0 - 0.1 * it) }
        val eta = HealthCalculator.eta(pts, 78.7, 75.0, today)
        assertNotNull(eta)
        assertEquals(-0.7, eta!!.kgPerWeek, 0.001)
        assertEquals(37, eta.daysToGoal)
        assertFalse(eta.tooFastWarning)
    }

    @Test
    fun tooFastWarning() {
        val today = 20_000L
        val pts = (0..10).map { WeightPoint(today - 10 + it, 90.0 - 0.2 * it) } // 1.4 kg/semana
        val eta = HealthCalculator.eta(pts, 88.0, 75.0, today)!!
        assertTrue(eta.tooFastWarning)
    }

    @Test
    fun etaNeedsEnoughData() {
        val today = 20_000L
        assertNull(HealthCalculator.eta(listOf(WeightPoint(today, 80.0), WeightPoint(today - 1, 80.2)), 80.0, 75.0, today))
    }

    @Test
    fun movingAverage() {
        val pts = listOf(WeightPoint(1, 80.0), WeightPoint(2, 82.0), WeightPoint(10, 70.0))
        val ma = HealthCalculator.movingAverage7(pts)
        assertEquals(80.0, ma[0].kg, 0.001)
        assertEquals(81.0, ma[1].kg, 0.001)
        assertEquals(70.0, ma[2].kg, 0.001) // fuera de la ventana de 7 días
    }

    @Test
    fun metKcal() {
        // Correr 10 km/h (9.8 MET), 70 kg, 30 min = 343 kcal
        assertEquals(343, HealthCalculator.exerciseKcal(9.8, 70.0, 30))
    }
}
